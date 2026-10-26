import {useCallback, useEffect, useRef, useState} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {ArrowRight, Check, CircleAlert, FolderTree, Info, Layers3, LoaderCircle, Pause, Play, ScanLine, Sparkles} from 'lucide-react'
import {useAnalysisReady} from '../desktop/readiness.js'
import {controlBatch, latestBatch, organizationKeys, startBatch} from './api.js'
import type {Batch} from './api.js'
import {libraryKeys} from '../library/api.js'
import {ReviewBoard} from './review/ReviewBoard.js'
import {OrganizationPlanPreview} from './OrganizationPlanPreview.js'
import './OrganizationPanel.css'
import {useUpdateBlocker} from '../desktop/updates/context.js'

const POLL_MS = 1200
const statuses = {RUNNING: 'Analiza w toku', PAUSED: 'Wstrzymano — postęp zachowany', COMPLETED: 'Analiza zakończona', CANCELLED: 'Zadanie anulowane'}

type Props = {folderPath: string; fileCount: number; scanning: boolean; active: boolean;
    onEditingChange?: (editing: boolean) => void; onExportingChange?: (busy: boolean) => void}

// A paused batch can still finish the image it was working on, so "processing" keeps polling alive too.
function inProgress(batch: Batch): boolean {
    return batch.status === 'RUNNING' || batch.processing > 0
}

function progressSignature(batch: Batch): string {
    return `${batch.id}:${batch.status}:${batch.completed}:${batch.failed}:${batch.processing}`
}

export function OrganizationPanel({folderPath, fileCount, scanning, active, onEditingChange, onExportingChange}: Props) {
    const ready = useAnalysisReady()
    const queryClient = useQueryClient()
    const [revision, setRevision] = useState(0)
    const [editingReview, setEditingReview] = useState(false)
    const [planOpen, setPlanOpen] = useState(false)
    const [exporting, setExporting] = useState(false)
    const [ocr, setOcr] = useState(true)
    const [visual, setVisual] = useState(true)

    const reportEditing = useCallback((editing: boolean) => {
        setEditingReview(editing); onEditingChange?.(editing)
    }, [onEditingChange])
    const reportExporting = useCallback((busy: boolean) => {setExporting(busy); onExportingChange?.(busy)}, [onExportingChange])
    useEffect(() => () => onEditingChange?.(false), [onEditingChange])

    // Only the batch is polled. The review board follows its counters (effect below), so one timer drives both.
    const batchQuery = useQuery({
        queryKey: organizationKeys.latest(folderPath),
        queryFn: ({signal}) => latestBatch(folderPath, signal),
        refetchInterval: query => {
            const batch = query.state.data
            return query.state.status !== 'error' && batch && inProgress(batch) ? POLL_MS : false
        },
        refetchIntervalInBackground: true,
    })
    const job = batchQuery.data ?? null
    const refreshBoard = useCallback(() => queryClient.invalidateQueries({queryKey: libraryKeys.folder(folderPath)}), [folderPath, queryClient])

    // A new result changes the counters: reload the board once instead of on every poll.
    const seenProgress = useRef<string | null>(null)
    useEffect(() => {
        if (!job) return
        const signature = progressSignature(job)
        const previous = seenProgress.current
        seenProgress.current = signature
        if (previous !== null && previous !== signature) {
            void refreshBoard()
        }
    }, [job, refreshBoard])

    const action = useMutation({
        mutationFn: (kind: 'START' | 'PAUSE' | 'RESUME' | 'CANCEL') =>
            kind === 'START' ? startBatch(folderPath, ocr, visual) : controlBatch(job?.id ?? '', kind),
        onSuccess: (next, kind) => {
            queryClient.setQueryData(organizationKeys.latest(folderPath), next)
            if (kind === 'START') setPlanOpen(false)
            void refreshBoard()
            setRevision(value => value + 1)
        },
    })
    const working = action.isPending

    function refreshState() {
        action.reset()
        void queryClient.invalidateQueries({queryKey: organizationKeys.latest(folderPath)})
        void refreshBoard()
        setRevision(value => value + 1)
    }

    const error = action.error?.message ?? batchQuery.error?.message ?? null
    const running = job?.status === 'RUNNING'
    useUpdateBlocker(running || working || (job?.processing ?? 0) > 0, 'Wstrzymaj analizę folderu i poczekaj na bieżący obraz przed restartem.')
    const paused = job?.status === 'PAUSED'
    const done = job?.status === 'COMPLETED' && job.processing === 0
    const finished = job ? job.completed + job.failed : 0
    const remaining = job ? Math.max(0, job.total - finished) : fileCount
    const blocked = !ready || scanning || running || working || editingReview || exporting || fileCount === 0
    return <section className="organization-panel" aria-labelledby="organize-title">
        {/* Once the analysis is done the review is the screen: the flow and the progress block step aside. */}
        {!done && <div className="organization-flow" aria-label="Przebieg pracy"><span data-done={fileCount > 0}><ScanLine size={14}/>Katalog<Check size={12}/></span><ArrowRight size={13}/>
            <span data-active={running}><Sparkles size={14}/>Analiza</span><ArrowRight size={13}/><span data-active={!!job && !running}><Layers3 size={14}/>Twoje decyzje</span></div>}
        <div className="organization-heading"><div><h2 id="organize-title">Analiza całego folderu</h2>
            <p>{done && job ? `Zakończona: ${job.completed} gotowych · ${job.failed} błędów.` : 'Obejmuje wszystkie zapisane obrazy folderu.'}</p></div>
            <button type="button" className="primary-button" onClick={() => action.mutate(paused ? 'RESUME' : 'START')} disabled={blocked}>
                {working || running ? <LoaderCircle size={18} className="spin"/> : paused ? <Play size={18}/> : <Sparkles size={18}/>}
                {running ? 'Analiza trwa…' : paused ? 'Wznów analizę (' + remaining + ')' : 'Analizuj wszystko (' + fileCount + ')'}
            </button>
        </div>
        {!job && <div className="analysis-scope" role="group" aria-label="Zakres analizy"><span>Co sprawdzamy?</span>
            {[{label: 'Tekst i obraz', ocr: true, visual: true}, {label: 'Tylko tekst', ocr: true, visual: false}, {label: 'Tylko obraz', ocr: false, visual: true}].map(option =>
                <button key={option.label} aria-pressed={ocr === option.ocr && visual === option.visual} disabled={working || running} onClick={() => {setOcr(option.ocr); setVisual(option.visual)}}>{option.label}</button>)}
        </div>}
        {!ready && <div className="feedback feedback-warning"><Info size={17}/><p>Uruchom <strong>silnik analizy</strong> w górnym pasku aplikacji. Katalog i skanowanie działają bez niego.</p></div>}
        {error && <div role="alert" className="feedback feedback-error"><CircleAlert size={18}/><div><p>{error}</p><button className="text-button" onClick={refreshState}>Odśwież stan</button></div></div>}
        {job ? <>
            {!done && <div className="batch-progress" data-state={job.failed > 0 ? 'warning' : job.status.toLowerCase()}>
                <div className="batch-progress-heading"><span aria-live="polite">{running ? <LoaderCircle size={17} className="spin"/> : job.failed > 0 ? <CircleAlert size={17}/> : <Check size={17}/>}
                    {job.status === 'COMPLETED' && job.failed > 0 ? 'Zakończono z błędami' : statuses[job.status]}</span><strong>{finished}<span> / {job.total}</span></strong></div>
                <progress max={Math.max(1, job.total)} value={finished} aria-label="Przetworzone obrazy"/>
                <div className="batch-progress-footer"><span>{job.completed} gotowych · {job.failed} błędów · {remaining} pozostałych</span>
                    <div className="button-row">{running && <button className="secondary-button" disabled={working} onClick={() => action.mutate('PAUSE')}><Pause size={15}/>Wstrzymaj</button>}
                        {running && <button className="text-button" disabled={working} onClick={() => action.mutate('CANCEL')}>Anuluj po bieżącym obrazie</button>}
                        {paused && <small>Wznowienie zachowuje już zapisane wyniki.</small>}</div></div>
                {paused && !ready && <p className="batch-resume-hint">Zachowano {finished} z {job.total} wyników. Po uruchomieniu silnika kliknij „Wznów analizę”.</p>}
            </div>}
            <div className="review-section-heading"><div><h3>Przegląd propozycji</h3></div>
                <div className="review-section-actions"><details className="proposal-explanation"><summary><Info size={15}/>Jak powstają propozycje?</summary><p>Nazwy łączą wyraźne słowa z tekstu na obrazie (np. tytuł gry) z opisem wizualnym, a kategorie powstają z prostych reguł OCR i opisu. Propozycje z małej ilości informacji są oznaczone jako niepewne. Zatwierdzenie zapisuje decyzję, nie zmienia pliku na dysku.</p></details>
                    {job.status === 'COMPLETED' && <button type="button" className="secondary-button" disabled={editingReview || working || exporting}
                        aria-expanded={planOpen} onClick={() => setPlanOpen(value => !value)}><FolderTree size={16}/>{planOpen ? 'Ukryj plan' : 'Zapisz i uporządkuj'}</button>}</div></div>
            {planOpen && <OrganizationPlanPreview batchId={job.id} revision={revision} disabled={editingReview || working || scanning || !!running}
                onClose={() => setPlanOpen(false)} onBusyChange={reportExporting}/>}
            <ReviewBoard key={job.id} folderPath={folderPath} batchId={job.id} disabled={scanning || exporting || working} active={active}
                onSaved={() => setRevision(value => value + 1)} onEditingChange={reportEditing}/>
            {paused && <button className="text-button" disabled={blocked} onClick={() => action.mutate('START')}>Zamiast wznawiać, rozpocznij nową analizę całego folderu</button>}
        </> : <div className="organization-empty"><span><Sparkles size={27}/></span><h3>Brak wyników analizy</h3><p>„Analizuj wszystko” obejmie wszystkie {fileCount} zapisanych obrazów w tym folderze. Po analizie pojawi się tutaj przegląd nazw i kategorii.</p><small>RapidOCR + Florence-2 · CPU · bez wysyłania zdjęć</small></div>}
    </section>
}

