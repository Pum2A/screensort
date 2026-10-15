import {useCallback, useEffect, useRef, useState} from 'react'
import {findAnalysisJob, latestAnalysis, startAnalysis} from './api'
import type {AnalysisJob, SavedAnalysis} from './types'
import {Icon} from '../ui/Icon'
import {formatDate} from '../catalog/format'
import styles from './AnalysisPanel.module.css'
import {useAnalysisReady} from '../desktop/readiness'

const labels = {PREPARING: 'Sprawdzamy oryginał…', ANALYZING: 'OCR i analiza wizualna…',
    SAVING: 'Zapisujemy wynik…', COMPLETED: 'Wynik zapisany', FAILED: 'Analiza nie powiodła się'}

export function AnalysisPanel({fileId}: {fileId: number}) {
    const analysisReady = useAnalysisReady()
    const [saved, setSaved] = useState<SavedAnalysis | null>(null)
    const [job, setJob] = useState<AnalysisJob | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(true)
    const [starting, setStarting] = useState(false)
    const [ocr, setOcr] = useState(true)
    const [visual, setVisual] = useState(true)
    const controller = useRef<AbortController | null>(null)
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
    const busy = starting || (job !== null && job.status !== 'COMPLETED' && job.status !== 'FAILED')
    const key = `screensort.analysis.job.${fileId}`

    const remember = useCallback((id: string | null) => {
        try { if (id) sessionStorage.setItem(key, id); else sessionStorage.removeItem(key) }
        catch { /* Preferences are optional; the backend remains the source of truth. */ }
    }, [key])

    const poll = useCallback(async function check(id: string, signal: AbortSignal) {
        try {
            const next = await findAnalysisJob(id, signal)
            if (signal.aborted) return
            if (!next) {
                const latest = await latestAnalysis(fileId, signal)
                if (!signal.aborted) { remember(null); setJob(null); setSaved(latest) }
                return
            }
            setJob(next)
            if (next.status === 'COMPLETED' || next.status === 'FAILED') {
                remember(null)
                if (next.analysis) setSaved(next.analysis)
                if (next.errorMessage) setError(next.errorMessage)
                return
            }
            timer.current = setTimeout(() => void check(id, signal), 800)
        } catch (cause) {
            if (!signal.aborted) setError(cause instanceof Error ? cause.message : 'Utracono połączenie z analizą.')
        }
    }, [fileId, remember])

    useEffect(() => {
        const abort = new AbortController()
        controller.current = abort
        async function load() {
            try {
                const result = await latestAnalysis(fileId, abort.signal)
                if (!abort.signal.aborted) setSaved(result)
                let id: string | null = null
                try { id = sessionStorage.getItem(key) } catch { /* Optional. */ }
                if (id && !abort.signal.aborted) await poll(id, abort.signal)
            } catch {
                if (!abort.signal.aborted) setError('Nie udało się odczytać zapisanej analizy.')
            } finally { if (!abort.signal.aborted) setLoading(false) }
        }
        void load()
        return () => { abort.abort(); if (timer.current) clearTimeout(timer.current) }
    }, [fileId, key, poll])

    async function start() {
        const signal = controller.current?.signal
        if (!signal || busy) return
        setStarting(true); setError(null)
        try {
            const next = await startAnalysis(fileId, ocr, visual, signal)
            if (signal.aborted) return
            setJob(next); remember(next.id)
            void poll(next.id, signal)
        } catch (cause) {
            if (!signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się rozpocząć analizy.')
        } finally { if (!signal.aborted) setStarting(false) }
    }

    return <div className={styles.panel}>
        <p className={styles.hint}>Lokalne modele analizują oryginał tylko do odczytu. Wynik zapisujemy w Twojej bazie.</p>
        <div className={styles.options}>
            <label><input type="checkbox" checked={ocr} disabled={busy} onChange={event => setOcr(event.target.checked)}/>Tekst OCR</label>
            <label><input type="checkbox" checked={visual} disabled={busy} onChange={event => setVisual(event.target.checked)}/>Opis obrazu</label>
        </div>
        <button type="button" className="primary-button" disabled={!analysisReady || loading || busy || (!ocr && !visual)} onClick={() => void start()}>
            <Icon name={busy ? 'refresh' : 'eye'} className={busy ? 'spin' : ''}/>
            {busy ? 'Analizujemy…' : saved ? 'Analizuj ponownie' : 'Analizuj obraz'}
        </button>
        {!analysisReady && <p role="status">Silniki analizy są jeszcze przygotowywane lub są niedostępne.</p>}
        {loading && <p role="status">Odczytujemy zapisane wyniki…</p>}
        {job && <p role="status">{labels[job.status]}</p>}
        {error && <div className={styles.error} role="alert"><p>{error}</p>
            {job && busy && <button type="button" className="text-button" onClick={() => {
                setError(null); if (controller.current) void poll(job.id, controller.current.signal)
            }}>Wznów sprawdzanie</button>}
        </div>}
        {saved && <div className={styles.results}>
            <p className={styles.hint}>Zapisano: {formatDate(saved.analyzedAt)}</p>
            {!saved.matchesCatalog && <p className={styles.warning}>Wynik dotyczy innych bajtów niż ostatni skan katalogu. Odśwież skan przed organizowaniem pliku.</p>}
            <h4>Opis wizualny</h4>
            {saved.result.visual.status === 'COMPLETED' ? <>
                <p>{saved.result.visual.result.description}</p>
                <small>{saved.result.visual.result.model} · {saved.result.visual.result.duration_ms.toFixed(0)} ms · {saved.result.visual.result.device.toUpperCase()}</small>
            </> : <p>{saved.result.visual.status === 'SKIPPED' ? 'Pominięto na Twoje życzenie.' : 'Model nie utworzył opisu. Możesz ponowić analizę.'}</p>}
            <h4>Tekst na obrazie</h4>
            {saved.result.ocr.status === 'COMPLETED' ? <>
                <pre>{saved.result.ocr.result.text || 'Nie rozpoznano napisów.'}</pre>
                <small>{saved.result.ocr.result.engine} · {saved.result.ocr.result.duration_ms.toFixed(0)} ms</small>
            </> : <p>{saved.result.ocr.status === 'SKIPPED' ? 'Pominięto na Twoje życzenie.' : 'OCR zakończył się błędem. Opis wizualny mógł się powieść.'}</p>}
            <details><summary>Szczegóły techniczne</summary><code>{saved.result.source_sha256}</code></details>
            <p className={styles.hint}>Opis modelu jest propozycją, nie gwarancją poprawności. Nie zmieniono nazwy ani zawartości zdjęcia.</p>
        </div>}
    </div>
}
