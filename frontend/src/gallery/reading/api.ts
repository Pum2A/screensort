import {requestJson} from '../../http/http.js'
import type {ErrorMessages} from '../../http/http.js'
import {scanJobSchema, scanRejectionsSchema} from './types.js'
import type {ScanJob, ScanRejections} from './types.js'

// The scan controller answers errors with plain text, not a code, so messages are mapped by status.
const messages: ErrorMessages = {
    byStatus: {
        400: 'Niepoprawne żądanie skanu.',
        404: 'Zadanie nie jest już dostępne. Backend mógł zostać uruchomiony ponownie lub usunąć starsze zadanie z historii.',
        409: 'Inny skan jest w toku lub wynik nie jest jeszcze gotowy.',
        503: 'Backend nie może teraz uruchomić skanu.',
    },
    fallback: 'Backend zwrócił błąd podczas obsługi skanu. Spróbuj ponownie.',
}

export const scanKeys = {
    all: ['scan'] as const,
    job: (id: string) => ['scan', 'job', id] as const,
    rejections: (id: string) => ['scan', 'job', id, 'rejections'] as const,
}

const jobPath = (id: string) => `/api/scan/jobs/${encodeURIComponent(id)}`

export function startScan(folderPath: string, signal?: AbortSignal): Promise<ScanJob> {
    return requestJson('/api/scan/jobs', scanJobSchema, messages, {method: 'POST', signal, body: {folderPath}})
}

export function fetchScanJob(id: string, signal?: AbortSignal): Promise<ScanJob> {
    return requestJson(jobPath(id), scanJobSchema, messages, {signal})
}

export function fetchRejections(id: string, signal?: AbortSignal): Promise<ScanRejections> {
    return requestJson(`${jobPath(id)}/rejections`, scanRejectionsSchema, messages, {signal})
}
