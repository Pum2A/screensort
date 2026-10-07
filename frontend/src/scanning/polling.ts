import {fetchScanJob} from './api.js'
import {isFinished} from './types.js'
import type {ScanJob} from './types.js'

export function waitForPoll(signal: AbortSignal, milliseconds = 1000): Promise<void> {
    signal.throwIfAborted()
    return new Promise((resolve, reject) => {
        const abort = () => {
            clearTimeout(timer)
            reject(signal.reason)
        }
        const timer = setTimeout(() => {
            signal.removeEventListener('abort', abort)
            resolve()
        }, milliseconds)
        signal.addEventListener('abort', abort, {once: true})
    })
}

/** Każde GET czeka na poprzednie. Nie ma setInterval ani ponownego POST. */
export async function observeScanJob(
    initial: ScanJob,
    onUpdate: (job: ScanJob) => void,
    signal: AbortSignal,
    read = fetchScanJob,
    wait = waitForPoll,
): Promise<ScanJob> {
    let job = initial
    signal.throwIfAborted()
    onUpdate(job)
    while (!isFinished(job)) {
        await wait(signal)
        signal.throwIfAborted()
        job = await read(initial.id, signal)
        signal.throwIfAborted()
        if (job.id !== initial.id) throw new Error('API zwróciło inne zadanie skanu.')
        onUpdate(job)
    }
    return job
}
