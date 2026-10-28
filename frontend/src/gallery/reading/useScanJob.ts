import {useEffect, useRef, useState} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {ApiError} from '../../shared/http.js'
import {fetchRejections, fetchScanJob, scanKeys, startScan} from './api.js'
import {isFinished, SCAN_POLL_MS, scanPollInterval} from './types.js'
import type {ScanJob} from './types.js'

export function useScanJob(onCompleted: (job: ScanJob) => void) {
    const queryClient = useQueryClient()
    const [jobId, setJobId] = useState<string | null>(null)
    // The completion callback fires once per job, however many renders show it as COMPLETED.
    const reportedId = useRef<string | null>(null)

    // Stopping the UI requests does NOT cancel the job the backend already accepted.
    const start = useMutation({
        mutationFn: (folderPath: string) => startScan(folderPath),
        onSuccess: initial => {
            queryClient.setQueryData(scanKeys.job(initial.id), initial)
            setJobId(initial.id)
        },
    })
    const jobQuery = useQuery({
        queryKey: scanKeys.job(jobId ?? ''),
        queryFn: async ({signal}) => {
            const job = await fetchScanJob(jobId ?? '', signal)
            if (job.id !== jobId) throw new ApiError('API zwróciło inne zadanie skanu.', 200, 'JOB_MISMATCH')
            return job
        },
        enabled: jobId !== null,
        // The ticket from POST is fresh: the first GET waits one interval, like every later one.
        staleTime: SCAN_POLL_MS,
        refetchInterval: query =>
            query.state.status === 'error' || !query.state.data ? false : scanPollInterval(query.state.data),
        // A minimized window must still notice the end of a scan (catalog refresh, update blocker).
        refetchIntervalInBackground: true,
    })
    const job = jobId === null ? null : jobQuery.data ?? null
    const completedId = job?.status === 'COMPLETED' ? job.id : null
    const rejectionsQuery = useQuery({
        queryKey: scanKeys.rejections(completedId ?? ''),
        queryFn: ({signal}) => fetchRejections(completedId ?? '', signal),
        enabled: completedId !== null,
    })

    useEffect(() => {
        if (!job || job.status !== 'COMPLETED' || reportedId.current === job.id) return
        reportedId.current = job.id
        onCompleted(job)
    }, [job, onCompleted])

    const lost = jobQuery.error instanceof ApiError && jobQuery.error.status === 404
    const error = start.error
        ? start.error instanceof ApiError ? start.error.message
            : 'Nie otrzymano ID zadania. Skan mógł już ruszyć — sprawdź backend przed ponownym startem.'
        : jobQuery.error
            ? jobQuery.error instanceof ApiError ? jobQuery.error.message
                : 'Utracono połączenie z backendem. Skan może nadal działać. Wznów sprawdzanie tego samego zadania.'
            : null
    // Routine poll ticks are not user operations; only a refetch after an error is.
    const requestActive = start.isPending || (jobQuery.isError && jobQuery.isFetching) || rejectionsQuery.isFetching

    function begin(folderPath: string) {
        if (start.isPending || (job && !isFinished(job) && !lost)) return
        setJobId(null)
        start.mutate(folderPath)
    }

    function resume() {
        if (!job || isFinished(job) || lost || requestActive) return
        void jobQuery.refetch()
    }

    function retryRejections() {
        if (completedId === null || requestActive) return
        void rejectionsQuery.refetch()
    }

    function clear() {
        start.reset()
        setJobId(null)
        queryClient.removeQueries({queryKey: scanKeys.all})
    }

    return {
        job, error, lost, requestActive,
        rejections: rejectionsQuery.data ?? null,
        rejectionsError: rejectionsQuery.error ? 'Skan zakończony, ale nie udało się pobrać powodów odrzucenia.' : null,
        busy: start.isPending || Boolean(job && !isFinished(job) && !lost),
        start: begin, resume, retryRejections, clear,
    }
}
