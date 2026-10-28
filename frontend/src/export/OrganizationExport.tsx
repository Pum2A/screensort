import {useEffect, useState} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {Check, CircleAlert, Copy, LoaderCircle, RefreshCw} from 'lucide-react'
import {useUpdateBlocker} from '../tauri/updates/context.js'
import {exportIssues, exportKeys, latestExport, startExport} from './api.js'

const POLL_MS = 800

type Props = {batchId: string; destinationFolder: string; totalApproved: number; disabled: boolean;
    onBusyChange: (busy: boolean) => void}

export function OrganizationExport({batchId, destinationFolder, totalApproved, disabled, onBusyChange}: Props) {
    const queryClient = useQueryClient()
    const [issuePage, setIssuePage] = useState(0)

    // Only one export runs per batch, so polling "latest" follows the same job the old per-ID loop did.
    const jobQuery = useQuery({
        queryKey: exportKeys.latest(batchId),
        queryFn: ({signal}) => latestExport(batchId, signal),
        refetchInterval: query =>
            query.state.status !== 'error' && query.state.data?.status === 'RUNNING' ? POLL_MS : false,
        refetchIntervalInBackground: true,
    })
    const job = jobQuery.data ?? null
    const issuesQuery = useQuery({
        queryKey: exportKeys.issues(job?.id ?? '', issuePage),
        queryFn: ({signal}) => exportIssues(job?.id ?? '', issuePage, signal),
        enabled: job !== null && job.status !== 'RUNNING' && job.failed > 0,
    })
    const start = useMutation({
        mutationFn: () => startExport(batchId, destinationFolder),
        onSuccess: created => {
            setIssuePage(0)
            queryClient.setQueryData(exportKeys.latest(batchId), created)
        },
    })

    const checking = jobQuery.isPending
    const requesting = start.isPending
    const busy = requesting || job?.status === 'RUNNING'
    useUpdateBlocker(busy, 'Poczekaj na zakończenie zapisywania uporządkowanych kopii przed restartem.')
    useEffect(() => {onBusyChange(busy)}, [busy, onBusyChange])
    useEffect(() => () => onBusyChange(false), [onBusyChange])

    function save() {
        // React re-renders between discrete clicks, so isPending already blocks a second POST.
        if (start.isPending || disabled || busy || checking || !destinationFolder.trim() || totalApproved === 0) return
        start.mutate()
    }

    function refreshState() {
        start.reset()
        void queryClient.invalidateQueries({queryKey: exportKeys.latest(batchId)})
        // The job key is a prefix of every issues page of that job.
        if (job) void queryClient.invalidateQueries({queryKey: exportKeys.job(job.id)})
    }

    const issues = issuesQuery.data ?? null
    // A later successful load replaces a failed start, as the old loop's setError(null) did.
    const startError = start.error && start.submittedAt >= jobQuery.dataUpdatedAt ? start.error.message : null
    const error = startError ?? jobQuery.error?.message ?? issuesQuery.error?.message ?? null
    const finished = job ? job.copied + job.failed : 0
    return <div className="organization-export">
        <div className="organization-export-action"><div><strong>Gotowe do zapisania?</strong>
            <p>Utworzymy osobny folder ScreenSort i skopiujemy do niego zatwierdzone obrazy według kategorii. Oryginały zostaną na miejscu.</p></div>
            <button type="button" className="primary-button" onClick={save}
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
            <button type="button" className="text-button" onClick={refreshState}><RefreshCw size={14}/>Odśwież stan</button></div></div>}
        {!!issues?.items.length && <details className="organization-export-issues"><summary>Sprawdź pominięte pliki ({job?.failed ?? 0})</summary>
            <ul>{issues.items.map((issue, index) => <li key={index}><strong>{issue.originalName}</strong><span>{issue.message}</span></li>)}</ul>
            <div className="button-row"><button type="button" className="text-button" disabled={issuePage === 0} onClick={() => setIssuePage(value => value - 1)}>Poprzednie</button>
                <button type="button" className="text-button" disabled={!issues.hasNext} onClick={() => setIssuePage(value => value + 1)}>Następne</button></div>
        </details>}
    </div>
}
