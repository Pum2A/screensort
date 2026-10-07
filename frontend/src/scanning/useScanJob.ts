import {useEffect, useRef, useState} from 'react'
import {fetchRejections, ScanApiError, startScan} from './api'
import {observeScanJob} from './polling'
import {isFinished} from './types'
import type {ScanJob, ScanRejections} from './types'

export function useScanJob(onCompleted: (job: ScanJob) => void) {
    const [job, setJob] = useState<ScanJob | null>(null)
    const [requestActive, setRequestActive] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [lost, setLost] = useState(false)
    const [rejections, setRejections] = useState<ScanRejections | null>(null)
    const [rejectionsError, setRejectionsError] = useState<string | null>(null)
    const operation = useRef<AbortController | null>(null)

    // Przerwanie zapytań UI NIE anuluje przyjętego zadania w Javie.
    useEffect(() => () => { operation.current?.abort() }, [])

    async function loadRejections(completed: ScanJob, signal: AbortSignal) {
        try {
            const details = await fetchRejections(completed.id, signal)
            signal.throwIfAborted()
            setRejections(details)
            setRejectionsError(null)
        } catch {
            if (!signal.aborted) setRejectionsError('Skan zakończony, ale nie udało się pobrać powodów odrzucenia.')
        }
    }

    async function track(initial: ScanJob, signal: AbortSignal) {
        const finished = await observeScanJob(initial, setJob, signal)
        if (finished.status === 'COMPLETED') {
            onCompleted(finished)
            await loadRejections(finished, signal)
        }
    }

    function beginOperation(): AbortController | null {
        // Ref blokuje drugi klik od razu, także przed następnym renderem Reacta.
        if (operation.current) return null
        const controller = new AbortController()
        operation.current = controller
        setRequestActive(true)
        setError(null)
        return controller
    }

    function finishOperation(controller: AbortController) {
        if (operation.current === controller) {
            operation.current = null
            if (!controller.signal.aborted) setRequestActive(false)
        }
    }

    function reportTrackingError(cause: unknown) {
        setLost(cause instanceof ScanApiError && cause.status === 404)
        setError(cause instanceof ScanApiError ? cause.message
            : 'Utracono połączenie z backendem. Skan może nadal działać. Wznów sprawdzanie tego samego zadania.')
    }

    async function start(folderPath: string) {
        if (job && !isFinished(job) && !lost) return
        const controller = beginOperation()
        if (!controller) return
        setJob(null)
        setLost(false)
        setRejections(null)
        setRejectionsError(null)
        let receivedId = false
        try {
            const initial = await startScan(folderPath, controller.signal)
            controller.signal.throwIfAborted()
            receivedId = true
            await track(initial, controller.signal)
        } catch (cause) {
            if (!controller.signal.aborted) {
                if (receivedId) reportTrackingError(cause)
                else setError(cause instanceof ScanApiError ? cause.message
                    : 'Nie otrzymano ID zadania. Skan mógł już ruszyć — sprawdź backend przed ponownym startem.')
            }
        } finally { finishOperation(controller) }
    }

    async function resume() {
        if (!job || isFinished(job) || lost) return
        const controller = beginOperation()
        if (!controller) return
        try { await track(job, controller.signal) }
        catch (cause) { if (!controller.signal.aborted) reportTrackingError(cause) }
        finally { finishOperation(controller) }
    }

    async function retryRejections() {
        if (job?.status !== 'COMPLETED') return
        const controller = beginOperation()
        if (!controller) return
        try { await loadRejections(job, controller.signal) }
        finally { finishOperation(controller) }
    }

    function clear() {
        operation.current?.abort()
        operation.current = null
        setJob(null)
        setRequestActive(false)
        setError(null)
        setLost(false)
        setRejections(null)
        setRejectionsError(null)
    }

    return {
        job, error, lost, rejections, rejectionsError, requestActive,
        busy: requestActive || Boolean(job && !isFinished(job) && !lost),
        start, resume, retryRejections, clear,
    }
}
