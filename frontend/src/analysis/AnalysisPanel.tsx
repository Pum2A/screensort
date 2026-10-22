import {useEffect, useState} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {analysisKeys, findAnalysisJob, latestAnalysis, startAnalysis} from './api.js'
import {isFinished} from './types.js'
import {Icon} from '../ui/Icon.js'
import {formatDate} from '../catalog/format.js'
import styles from './AnalysisPanel.module.css'
import {useAnalysisReady} from '../desktop/readiness.js'
import {NamingPanel} from '../naming/NamingPanel.js'
import {useUpdateBlocker} from '../desktop/updates/context.js'

const POLL_MS = 800
const labels = {PREPARING: 'Sprawdzamy oryginał…', ANALYZING: 'OCR i analiza wizualna…',
    SAVING: 'Zapisujemy wynik…', COMPLETED: 'Wynik zapisany', FAILED: 'Analiza nie powiodła się'}

// sessionStorage lets a running job survive a page reload. It is optional: the backend stays the source of truth.
function storageKey(fileId: number): string {
    return `screensort.analysis.job.${fileId}`
}

function rememberedJob(fileId: number): string | null {
    try { return sessionStorage.getItem(storageKey(fileId)) } catch { return null }
}

function remember(fileId: number, id: string | null): void {
    try { if (id) sessionStorage.setItem(storageKey(fileId), id); else sessionStorage.removeItem(storageKey(fileId)) }
    catch { /* Preferences are optional. */ }
}

// The parent renders this panel with key={fileId}, so all state below belongs to one image.
export function AnalysisPanel({fileId}: {fileId: number}) {
    const analysisReady = useAnalysisReady()
    const queryClient = useQueryClient()
    const [ocr, setOcr] = useState(true)
    const [visual, setVisual] = useState(true)
    const [jobId, setJobId] = useState(() => rememberedJob(fileId))

    const savedQuery = useQuery({
        queryKey: analysisKeys.latest(fileId),
        queryFn: ({signal}) => latestAnalysis(fileId, signal),
    })
    const jobQuery = useQuery({
        queryKey: analysisKeys.job(jobId ?? ''),
        queryFn: ({signal}) => findAnalysisJob(jobId ?? '', signal),
        enabled: jobId !== null,
        // Poll until the job is final. Query stops the timer on its own when the panel unmounts.
        refetchInterval: query => {
            if (query.state.status === 'error') return false
            const job = query.state.data
            return job === undefined || (job !== null && !isFinished(job)) ? POLL_MS : false
        },
    })
    const start = useMutation({
        mutationFn: () => startAnalysis(fileId, ocr, visual),
        onSuccess: next => {
            remember(fileId, next.id)
            queryClient.setQueryData(analysisKeys.job(next.id), next)
            setJobId(next.id)
        },
    })

    // React to the backend's answer about the job: this syncs the cache and sessionStorage, not React state.
    const jobAnswer = jobQuery.data
    useEffect(() => {
        if (jobAnswer === undefined) return
        if (jobAnswer === null) {
            // The job expired on the backend (404); the saved analysis is the truth.
            remember(fileId, null)
            void queryClient.invalidateQueries({queryKey: analysisKeys.latest(fileId)})
        } else if (isFinished(jobAnswer)) {
            remember(fileId, null)
            if (jobAnswer.analysis) queryClient.setQueryData(analysisKeys.latest(fileId), jobAnswer.analysis)
        }
    }, [jobAnswer, fileId, queryClient])

    const saved = savedQuery.data ?? null
    const job = jobAnswer ?? null
    const busy = start.isPending || (job !== null && !isFinished(job))
    const loading = savedQuery.isPending
    const error = start.error?.message
        ?? (job?.status === 'FAILED' ? job.errorMessage : null)
        ?? jobQuery.error?.message
        ?? (savedQuery.error ? 'Nie udało się odczytać zapisanej analizy.' : null)
    useUpdateBlocker(busy, 'Poczekaj na zakończenie analizy wybranego obrazu przed restartem.')

    return <div className={styles.panel}>
        <p className={styles.hint}>Lokalne modele analizują oryginał tylko do odczytu. Wynik zapisujemy w Twojej bazie.</p>
        <div className={styles.options}>
            <label><input type="checkbox" checked={ocr} disabled={busy} onChange={event => setOcr(event.target.checked)}/>Tekst OCR</label>
            <label><input type="checkbox" checked={visual} disabled={busy} onChange={event => setVisual(event.target.checked)}/>Opis obrazu</label>
        </div>
        <button type="button" className="primary-button" disabled={!analysisReady || loading || busy || (!ocr && !visual)}
            onClick={() => start.mutate()}>
            <Icon name={busy ? 'refresh' : 'eye'} className={busy ? 'spin' : ''}/>
            {busy ? 'Analizujemy…' : saved ? 'Analizuj ponownie' : 'Analizuj obraz'}
        </button>
        {!analysisReady && <p role="status">Silniki analizy są jeszcze przygotowywane lub są niedostępne.</p>}
        {loading && <p role="status">Odczytujemy zapisane wyniki…</p>}
        {job && <p role="status">{labels[job.status]}</p>}
        {error && <div className={styles.error} role="alert"><p>{error}</p>
            {job && busy && <button type="button" className="text-button" onClick={() => void jobQuery.refetch()}>
                Wznów sprawdzanie
            </button>}
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
            <NamingPanel key={`${saved.id}:${saved.analyzedAt}:${saved.result.source_sha256}`}
                fileId={fileId} disabled={busy}/>
        </div>}
    </div>
}
