import {useCallback, useContext, useEffect, useRef, useState} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {ArrowRight, Check, CircleAlert, FolderTree, Info, Layers3, LoaderCircle, Pause, Play, ScanLine, Sparkles} from 'lucide-react'
import {useAnalysisReady} from '../tauri/readiness.js'
import {DesktopRuntime} from '../tauri/runtimeContext.js'
import {controlBatch, latestBatch, organizationKeys, startBatch} from './batchApi.js'
import {organizeStep, wantsAnalysis} from './organize.js'
import {useAutoAnalysis} from './autoAnalysis.js'
import type {Batch} from './batchApi.js'
import {libraryKeys} from '../gallery/libraryApi.js'
import {ReviewBoard} from '../review/ReviewBoard.js'
import {OrganizationPlanPreview} from '../export/OrganizationPlanPreview.js'
import './OrganizationPanel.css'
import {useUpdateBlocker} from '../tauri/updates/context.js'

const POLL_MS = 1200
const statuses = {RUNNING: 'Analiza w toku', PAUSED: 'Wstrzymano — postęp zachowany', COMPLETED: 'Analiza zakończona', CANCELLED: 'Zadanie anulowane'}

/** "Uporządkuj z AI" clicked in the library: analyse this folder once the engine is ready. */
export type OrganizeRequest = {folder: string; id: number}

type Props = {folderPath: string; fileCount: number; scanning: boolean; active: boolean;
    /** False while the file count is still loading: a request waits instead of reading a stale 0. */
    countReady?: boolean
    organizeRequest?: OrganizeRequest | null
    onEditingChange?: (editing: boolean) => void; onExportingChange?: (busy: boolean) => void}

// A paused batch can still finish the image it was working on, so "processing" keeps polling alive too.
function inProgress(batch: Batch): boolean {
    return batch.status === 'RUNNING' || batch.processing > 0
}

function progressSignature(batch: Batch): string {
    return `${batch.id}:${batch.status}:${batch.completed}:${batch.failed}:${batch.processing}`
}

export function OrganizationPanel({folderPath, fileCount, scanning, active, countReady = true, organizeRequest = null, onEditingChange, onExportingChange}: Props) {
    const ready = useAnalysisReady()
    const runtime = useContext(DesktopRuntime)
    // Set by a click while the engine is not ready yet: start the engine, then the analysis.
    const [pending, setPending] = useState(false)
    const autoAnalysis = useAutoAnalysis()
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
    // On the desktop the button may start the engine itself; only the browser needs a ready engine first.
    const blocked = (!ready && !runtime) || pending || scanning || running || working || editingReview || exporting || fileCount === 0
    const busyElsewhere = scanning || running || working || editingReview || exporting
    const engineError = runtime?.status.error ?? null

    // A completed analysis is not repeated unless the folder gained images since; a paused one resumes.
    const handledRequest = useRef<number | null>(null)
    useEffect(() => {
        // An unknown batch state or file count must not start a second analysis by mistake: wait for both.
        if (!organizeRequest || organizeRequest.folder !== folderPath || organizeRequest.id === handledRequest.current
            || !batchQuery.isSuccess || !countReady) return
        handledRequest.current = organizeRequest.id
        // In the browser nothing here can start the engine; its warning explains what to do instead.
        if (fileCount > 0 && (ready || runtime) && wantsAnalysis(job, fileCount)) setPending(true)
    }, [organizeRequest, folderPath, batchQuery.isSuccess, countReady, job, fileCount, ready, runtime])

    const engineRequested = useRef(false)
    const mutate = action.mutate
    useEffect(() => {
        if (!pending) {engineRequested.current = false; return}
        const step = organizeStep({ready, busyElsewhere, fileCount, desktop: !!runtime,
            engineStarting: runtime?.status.analysisStarting ?? false, engineError, engineRequested: engineRequested.current})
        if (step === 'analyze') {setPending(false); mutate(paused ? 'RESUME' : 'START')}
        else if (step === 'start-engine') {engineRequested.current = true; runtime?.start()}
        else if (step === 'give-up') setPending(false)
    }, [pending, ready, busyElsewhere, fileCount, paused, runtime, engineError, mutate])

    function organize() {
        if (ready) action.mutate(paused ? 'RESUME' : 'START')
        else setPending(true)
    }
    return <section className="organization-panel" aria-labelledby="organize-title">
        {/* Once the analysis is done the review is the screen: the flow and the progress block step aside. */}
        {!done && <div className="organization-flow" aria-label="Przebieg pracy"><span data-done={fileCount > 0}><ScanLine size={14}/>Katalog<Check size={12}/></span><ArrowRight size={13}/>
            <span data-active={running}><Sparkles size={14}/>Analiza</span><ArrowRight size={13}/><span data-active={!!job && !running}><Layers3 size={14}/>Twoje decyzje</span></div>}
        <div className="organization-heading"><div><h2 id="organize-title">Uporządkuj z AI</h2>
            <p>{done && job ? `Zakończona: ${job.completed} gotowych · ${job.failed} błędów.` : 'AI nazwie i przydzieli do kategorii wszystkie obrazy folderu.'}</p></div>
            <button type="button" className="primary-button" onClick={organize} disabled={blocked}>
                {working || running || pending ? <LoaderCircle size={18} className="spin"/> : paused ? <Play size={18}/> : <Sparkles size={18}/>}
                {pending ? 'Uruchamiamy silnik AI…' : running ? 'Analiza trwa…' : paused ? 'Wznów analizę (' + remaining + ')' : 'Uporządkuj z AI (' + fileCount + ')'}
            </button>
        </div>
        <label className="auto-analysis"><input type="checkbox" checked={autoAnalysis.enabled} disabled={autoAnalysis.loading}
            onChange={event => autoAnalysis.change(event.target.checked)}/>
            <span>Analizuj nowe zdjęcia automatycznie<small>Gdy w folderze pojawią się nowe zrzuty, AI od razu je nazwie i przydzieli kategorię.</small></span></label>
        {autoAnalysis.error && <small role="alert" className="auto-analysis-error">{autoAnalysis.error}</small>}
        {!job && <div className="analysis-scope" role="group" aria-label="Zakres analizy"><span>Co sprawdzamy?</span>
            {[{label: 'Tekst i obraz', ocr: true, visual: true}, {label: 'Tylko tekst', ocr: true, visual: false}, {label: 'Tylko obraz', ocr: false, visual: true}].map(option =>
                <button key={option.label} aria-pressed={ocr === option.ocr && visual === option.visual} disabled={working || running} onClick={() => {setOcr(option.ocr); setVisual(option.visual)}}>{option.label}</button>)}
        </div>}
        {!ready && !pending && (runtime
            ? engineError
                ? <div className="feedback feedback-error" role="alert"><CircleAlert size={17}/><p>{engineError} Pakiet AI zainstalujesz w karcie <strong>Silnik AI</strong> w menu bocznym.</p></div>
                : <div className="feedback feedback-neutral"><Info size={17}/><p>„Uporządkuj z AI” samo uruchomi silnik analizy. Pierwsze uruchomienie po starcie aplikacji trwa chwilę.</p></div>
            : <div className="feedback feedback-warning"><Info size={17}/><p>Uruchom <strong>silnik analizy</strong>. Katalog działa bez niego.</p></div>)}
        {pending && <p className="inline-loading" role="status"><LoaderCircle size={17} className="spin"/>
            {ready ? 'Analiza ruszy, gdy zakończy się bieżąca operacja.' : 'Ładujemy model AI. Analiza ruszy sama, gdy będzie gotowy.'}
            <button type="button" className="text-button" onClick={() => setPending(false)}>Anuluj</button></p>}
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
                {paused && !ready && <p className="batch-resume-hint">Zachowano {finished} z {job.total} wyników. „Wznów analizę” uruchomi silnik i dokończy resztę.</p>}
            </div>}
            <div className="review-section-heading"><div><h3>Przegląd propozycji</h3></div>
                <div className="review-section-actions"><details className="proposal-explanation"><summary><Info size={15}/>Jak powstają propozycje?</summary><p>Model AI rozpoznaje aplikację i temat zrzutu, a z tego powstaje nazwa (np. discord_planowanie_wyjazdu) i kategoria. Bez modelu Qwen nazwy łączą słowa z tekstu na obrazie z opisem wizualnym. Propozycje z małej ilości informacji są oznaczone jako niepewne. Zatwierdzenie zapisuje decyzję, nie zmienia pliku na dysku.</p></details>
                    {job.status === 'COMPLETED' && <button type="button" className="secondary-button" disabled={editingReview || working || exporting}
                        aria-expanded={planOpen} onClick={() => setPlanOpen(value => !value)}><FolderTree size={16}/>{planOpen ? 'Ukryj plan' : 'Zapisz i uporządkuj'}</button>}</div></div>
            {planOpen && <OrganizationPlanPreview batchId={job.id} revision={revision} disabled={editingReview || working || scanning || !!running}
                onClose={() => setPlanOpen(false)} onBusyChange={reportExporting}/>}
            <ReviewBoard key={job.id} folderPath={folderPath} batchId={job.id} disabled={scanning || exporting || working} active={active}
                onSaved={() => setRevision(value => value + 1)} onEditingChange={reportEditing}/>
            {paused && <button className="text-button" disabled={blocked} onClick={() => action.mutate('START')}>Zamiast wznawiać, rozpocznij nową analizę całego folderu</button>}
        </> : <div className="organization-empty"><span><Sparkles size={27}/></span><h3>Brak wyników analizy</h3><p>„Uporządkuj z AI” obejmie wszystkie {fileCount} obrazów w tym folderze. Po analizie pojawi się tutaj przegląd nazw i kategorii.</p><small>AI działa na Twoim komputerze · bez wysyłania zdjęć</small></div>}
    </section>
}

