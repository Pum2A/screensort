import type {ScanJob, ScanRejections} from './types.js'
import {backendFetch} from '../desktop/transport.js'

export class ScanApiError extends Error {
    readonly status: number

    constructor(status: number, message: string) {
        super(message)
        this.status = status
    }
}

type Fetcher = (url: string, options?: RequestInit) => Promise<Response>

async function request<T>(url: string, options: RequestInit, fetcher: Fetcher): Promise<T> {
    const response = await fetcher(url, {...options, cache: 'no-store'})
    if (!response.ok) {
        const messages: Record<number, string> = {
            400: 'Niepoprawne żądanie skanu.',
            404: 'Zadanie nie jest już dostępne. Backend mógł zostać uruchomiony ponownie lub usunąć starsze zadanie z historii.',
            409: 'Inny skan jest w toku lub wynik nie jest jeszcze gotowy.',
            503: 'Backend nie może teraz uruchomić skanu.',
        }
        throw new ScanApiError(response.status, messages[response.status] ?? `Błąd API (HTTP ${response.status}).`)
    }
    return await response.json() as T
}

export function startScan(folderPath: string, signal: AbortSignal, fetcher = backendFetch): Promise<ScanJob> {
    return request('/api/scan/jobs', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({folderPath}), signal,
    }, fetcher)
}

export function fetchScanJob(id: string, signal: AbortSignal, fetcher = backendFetch): Promise<ScanJob> {
    return request(`/api/scan/jobs/${encodeURIComponent(id)}`, {signal}, fetcher)
}

export function fetchRejections(id: string, signal: AbortSignal, fetcher = backendFetch): Promise<ScanRejections> {
    return request(`/api/scan/jobs/${encodeURIComponent(id)}/rejections`, {signal}, fetcher)
}
