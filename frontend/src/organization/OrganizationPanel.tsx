import {useCallback, useEffect, useRef, useState} from 'react'
import {ArrowRight, Check, CircleAlert, Info, Layers3, LoaderCircle, Pause, Play, ScanLine, Sparkles} from 'lucide-react'
import {useAnalysisReady} from '../desktop/readiness'
import {batchItems, controlBatch, latestBatch, readBatch, startBatch} from './api'
import type {Batch, BatchItem, BatchPage} from './api'
import {ReviewQueue} from './review/ReviewQueue'
import './OrganizationPanel.css'
import {useUpdateBlocker} from '../desktop/updates/context'

const statuses = {RUNNING: 'Analiza w toku', PAUSED: 'Wstrzymano — postęp zachowany', COMPLETED: 'Analiza zakończona', CANCELLED: 'Zadanie anulowane'}

type Props = {folderPath: string; fileCount: number; scanning: boolean; onEditingChange?: (editing: boolean) => void}

export function OrganizationPanel({folderPath, fileCount, scanning, onEditingChange}: Props) {
    const ready = useAnalysisReady()
    const [job, setJob] = useState<Batch | null>(null)
    const [items, setItems] = useState<BatchPage | null>(null)
    const [page, setPage] = useState(0)
    const [refresh, setRefresh] = useState(0)
    const [error, setError] = useState<string | null>(null)
    const [working, setWorking] = useState(false)
    const [editingReview, setEditingReview] = useState(false)
    const [ocr, setOcr] = useState(true)
    const [visual, setVisual] = useState(true)
    const abort = useRef<AbortController | null>(null)

    const reportEditing = useCallback((editing: boolean) => {
        setEditingReview(editing); onEditingChange?.(editing)
    }, [onEditingChange])
    useEffect(() => () => onEditingChange?.(false), [onEditingChange])

    useEffect(() => {
        const controller = new AbortController()
        abort.current = controller
        let timer: ReturnType<typeof setTimeout>
        async function load(id?: string) {
            try {
                const next = id ? await readBatch(id, controller.signal) : await latestBatch(folderPath, controller.signal)
                if (controller.signal.aborted) return
                if (!next) {setJob(null); setItems(null); return}
                const result = await batchItems(next.id, page, controller.signal)
                if (controller.signal.aborted) return
                setJob(next); setItems(result)
                if (next.status === 'RUNNING' || next.processing > 0) timer = setTimeout(() => void load(next.id), 1200)
            } catch (cause) {
                if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się pobrać postępu.')
            }
        }
        void load()
        return () => {controller.abort(); clearTimeout(timer)}
    }, [folderPath, page, refresh])

    async function action(kind: 'START' | 'PAUSE' | 'RESUME' | 'CANCEL') {
        const signal = abort.current?.signal
        if (!signal || working) return
        setWorking(true); setError(null)
        try {
            const next = kind === 'START' ? await startBatch(folderPath, ocr, visual, signal) : await controlBatch(job!.id, kind, signal)
            if (signal.aborted) return
            abort.current?.abort(); setJob(next)
            if (kind === 'START') {setItems(null); setPage(0)}
            setRefresh(value => value + 1)
        } catch (cause) {
            if (!signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się zlecić zadania.')
        } finally {setWorking(false)}
    }

    function reviewed(item: BatchItem) {
        abort.current?.abort()
        setItems(current => current && {...current, items: current.items.map(value => value.fileId === item.fileId ? item : value)})
        setRefresh(value => value + 1)
    }

    const running = job?.status === 'RUNNING'
    useUpdateBlocker(running || working || (job?.processing ?? 0) > 0, 'Wstrzymaj analizę folderu i poczekaj na bieżący obraz przed restartem.')
    const paused = job?.status === 'PAUSED'
    const finished = job ? job.completed + job.failed : 0
    const remaining = job ? Math.max(0, job.total - finished) : fileCount
    const blocked = !ready || scanning || running || working || editingReview || fileCount === 0
    return <section className="organization-panel" aria-labelledby="organize-title">
        <div className="organization-flow" aria-label="Przebieg pracy"><span data-done={fileCount > 0}><ScanLine size={14}/>Katalog<Check size={12}/></span><ArrowRight size={13}/>
            <span data-active={running}><Sparkles size={14}/>Analiza</span><ArrowRight size={13}/><span data-active={!!job && !running}><Layers3 size={14}/>Twoje decyzje</span></div>
        <div className="organization-heading"><div><h2 id="organize-title">Analiza całego folderu</h2>
            <p>Obejmuje wszystkie zapisane obrazy, nie tylko tę stronę.</p></div>
            <button type="button" className="primary-button" onClick={() => void action(paused ? 'RESUME' : 'START')} disabled={blocked}>
                {working || running ? <LoaderCircle size={18} className="spin"/> : paused ? <Play size={18}/> : <Sparkles size={18}/>}
                {running ? 'Analiza trwa…' : paused ? 'Wznów analizę (' + remaining + ')' : 'Analizuj wszystko (' + fileCount + ')'}
            </button>
        </div>
        {!job && <div className="analysis-scope" role="group" aria-label="Zakres analizy"><span>Co sprawdzamy?</span>
            {[{label: 'Tekst i obraz', ocr: true, visual: true}, {label: 'Tylko tekst', ocr: true, visual: false}, {label: 'Tylko obraz', ocr: false, visual: true}].map(option =>
                <button key={option.label} aria-pressed={ocr === option.ocr && visual === option.visual} disabled={working || running} onClick={() => {setOcr(option.ocr); setVisual(option.visual)}}>{option.label}</button>)}
        </div>}
        {!ready && <div className="feedback feedback-warning"><Info size={17}/><p>Uruchom <strong>silnik analizy</strong> w panelu po lewej. Katalog i skanowanie działają bez niego.</p></div>}
        {error && <div role="alert" className="feedback feedback-error"><CircleAlert size={18}/><div><p>{error}</p><button className="text-button" onClick={() => {setError(null); setRefresh(value => value + 1)}}>Odśwież stan</button></div></div>}
        {job ? <>
            <div className="batch-progress" data-state={job.failed > 0 ? 'warning' : job.status.toLowerCase()}>
                <div className="batch-progress-heading"><span aria-live="polite">{running ? <LoaderCircle size={17} className="spin"/> : job.failed > 0 ? <CircleAlert size={17}/> : <Check size={17}/>}
                    {job.status === 'COMPLETED' && job.failed > 0 ? 'Zakończono z błędami' : statuses[job.status]}</span><strong>{finished}<span> / {job.total}</span></strong></div>
                <progress max={Math.max(1, job.total)} value={finished} aria-label="Przetworzone obrazy"/>
                <div className="batch-progress-footer"><span>{job.completed} gotowych · {job.failed} błędów · {remaining} pozostałych</span>
                    <div className="button-row">{running && <button className="secondary-button" disabled={working} onClick={() => void action('PAUSE')}><Pause size={15}/>Wstrzymaj</button>}
                        {running && <button className="text-button" disabled={working} onClick={() => void action('CANCEL')}>Anuluj po bieżącym obrazie</button>}
                        {paused && <small>Wznowienie zachowuje już zapisane wyniki.</small>}</div></div>
                {paused && !ready && <p className="batch-resume-hint">Zachowano {finished} z {job.total} wyników. Po uruchomieniu silnika kliknij „Wznów analizę”.</p>}
            </div>
            <div className="review-section-heading"><div><span className="eyebrow">PROPOZYCJE DO TWOJEJ WERYFIKACJI</span><h3>Wybierz obraz. Nadaj mu kontekst.</h3></div>
                <details className="proposal-explanation"><summary><Info size={15}/>Jak powstają propozycje?</summary><p>Nazwy pochodzą obecnie z opisu wizualnego, a kategorie z prostych reguł OCR i opisu. To nie jest jeszcze wspólna interpretacja. Zatwierdzenie zapisuje decyzję, nie zmienia pliku na dysku.</p></details></div>
            {items ? <ReviewQueue key={job.id + ':' + page} batchId={job.id} items={items.items} disabled={scanning} onSaved={reviewed} onEditingChange={reportEditing}/>
                : <p className="inline-loading" role="status"><LoaderCircle size={17} className="spin"/>Wczytujemy propozycje…</p>}
            <nav className="catalog-pagination" aria-label="Strony propozycji"><span className="page-range">Kategorie i liczniki dotyczą tej strony propozycji.</span><div className="page-controls">
                <button className="icon-button" aria-label="Poprzednie propozycje" disabled={page === 0 || !items || working || editingReview} onClick={() => {setItems(null); setPage(value => value - 1)}}><IconLeft/></button>
                <span>Strona {page + 1}</span><button className="icon-button" aria-label="Następne propozycje" disabled={!items?.hasNext || working || editingReview} onClick={() => {setItems(null); setPage(value => value + 1)}}><IconRight/></button></div></nav>
            {editingReview && <p className="review-draft-hint">Przed zmianą strony zapisz lub odrzuć edycję. Samo przełączenie na katalog jej nie usuwa.</p>}
            {paused && <button className="text-button" disabled={blocked} onClick={() => void action('START')}>Zamiast wznawiać, rozpocznij nową analizę całego folderu</button>}
        </> : <div className="organization-empty"><span><Sparkles size={27}/></span><h3>Najpierw zobaczmy, co jest w środku.</h3><p>„Analizuj wszystko” obejmie wszystkie {fileCount} zapisanych obrazów w tym folderze. Po analizie pojawi się tutaj przegląd nazw i kategorii.</p><small>RapidOCR + Florence-2 · CPU · bez wysyłania zdjęć</small></div>}
    </section>
}

function IconLeft() {return <ArrowRight size={16} style={{transform: 'rotate(180deg)'}}/>}
function IconRight() {return <ArrowRight size={16}/>}
