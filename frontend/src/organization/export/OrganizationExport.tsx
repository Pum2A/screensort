import {useEffect, useRef, useState} from 'react'
import {Check, CircleAlert, Copy, LoaderCircle, RefreshCw} from 'lucide-react'
import {useUpdateBlocker} from '../../desktop/updates/context.js'
import {exportIssues, latestExport, readExport, startExport} from './api.js'
import type {ExportIssues, ExportJob} from './api.js'

type Props = {batchId: string; destinationFolder: string; totalApproved: number; disabled: boolean;
    onBusyChange: (busy: boolean) => void}

export function OrganizationExport({batchId, destinationFolder, totalApproved, disabled, onBusyChange}: Props) {
    const [job, setJob] = useState<ExportJob | null>(null)
    const [issues, setIssues] = useState<ExportIssues | null>(null)
    const [issuePage, setIssuePage] = useState(0)
    const [checking, setChecking] = useState(true)
    const [requesting, setRequesting] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [refresh, setRefresh] = useState(0)
    const request = useRef(false)
    const busy = requesting || job?.status === 'RUNNING'
    useUpdateBlocker(busy, 'Poczekaj na zakończenie zapisywania uporządkowanych kopii przed restartem.')
    useEffect(() => {onBusyChange(busy)}, [busy, onBusyChange])
    useEffect(() => () => onBusyChange(false), [onBusyChange])

    useEffect(() => {
        const controller = new AbortController()
        let timer: ReturnType<typeof setTimeout> | undefined
        async function load(id?: string) {
            try {
                const current = id ? await readExport(id, controller.signal) : await latestExport(batchId, controller.signal)
                if (controller.signal.aborted) return
                setJob(current); setChecking(false); setError(null)
                if (current?.status === 'RUNNING') timer = setTimeout(() => void load(current.id), 800)
                else if (current && current.failed > 0) {
                    const failures = await exportIssues(current.id, issuePage, controller.signal)
                    if (!controller.signal.aborted) setIssues(failures)
                }
            } catch (cause) {
                if (!controller.signal.aborted) {
                    setError(cause instanceof Error ? cause.message : 'Nie udało się pobrać stanu kopiowania.')
                    setChecking(false)
                }
            }
        }
        void load()
        return () => {controller.abort(); clearTimeout(timer)}
    }, [batchId, refresh, issuePage])

    async function save() {
        if (request.current || disabled || busy || checking || !destinationFolder.trim() || totalApproved === 0) return
        request.current = true; setRequesting(true); setError(null)
        try {
            const created = await startExport(batchId, destinationFolder)
            setJob(created); setIssues(null); setIssuePage(0); setRefresh(value => value + 1)
        } catch (cause) {setError(cause instanceof Error ? cause.message : 'Nie udało się rozpocząć kopiowania.')}
        finally {request.current = false; setRequesting(false)}
    }

    const finished = job ? job.copied + job.failed : 0
    return <div className="organization-export">
        <div className="organization-export-action"><div><strong>Gotowe do zapisania?</strong>
            <p>Utworzymy osobny folder ScreenSort i skopiujemy do niego zatwierdzone obrazy według kategorii. Oryginały zostaną na miejscu.</p></div>
            <button type="button" className="primary-button" onClick={() => void save()}
                disabled={disabled || busy || checking || !destinationFolder.trim() || totalApproved === 0}>
                {busy || checking ? <LoaderCircle size={17} className="spin"/> : <Copy size={17}/>}
                {busy ? 'Zapisujemy kopie…' : checking ? 'Sprawdzamy zadanie…' : 'Zapisz uporządkowaną kopię'}
            </button></div>
        {job && <div className="organization-export-progress" data-state={job.status === 'RUNNING' ? 'running' : job.failed > 0 || job.status === 'INTERRUPTED' ? 'warning' : 'complete'} role="status">
            <div><span>{job.status === 'RUNNING' ? <LoaderCircle size={17} className="spin"/> : job.failed > 0 || job.status === 'INTERRUPTED' ? <CircleAlert size={17}/> : <Check size={17}/>}
                {job.status === 'RUNNING' ? 'Tworzymy uporządkowaną kopię' : job.status === 'INTERRUPTED' ? 'Kopiowanie przerwane' : job.failed ? 'Zakończono z pominiętymi plikami' : 'Uporządkowana kopia jest gotowa'}</span>
                <strong>{finished} / {job.total}</strong></div>
            <progress max={Math.max(1, job.total)} value={finished} aria-label="Postęp zapisywania kopii"/>
            <p>{job.copied} zapisanych · {job.failed} pominiętych</p>
            <label>Folder wynikowy<input readOnly value={job.outputFolder} aria-label="Ścieżka folderu wynikowego" onFocus={event => event.target.select()}/></label>
            {job.message && <p>{job.message}</p>}
        </div>}
        {error && <div className="feedback feedback-error" role="alert"><CircleAlert size={17}/><div><p>{error}</p>
            <button type="button" className="text-button" onClick={() => setRefresh(value => value + 1)}><RefreshCw size={14}/>Odśwież stan</button></div></div>}
        {!!issues?.items.length && <details className="organization-export-issues"><summary>Sprawdź pominięte pliki ({job?.failed ?? 0})</summary>
            <ul>{issues.items.map((issue, index) => <li key={index}><strong>{issue.originalName}</strong><span>{issue.message}</span></li>)}</ul>
            <div className="button-row"><button type="button" className="text-button" disabled={issuePage === 0} onClick={() => setIssuePage(value => value - 1)}>Poprzednie</button>
                <button type="button" className="text-button" disabled={!issues.hasNext} onClick={() => setIssuePage(value => value + 1)}>Następne</button></div>
        </details>}
    </div>
}
