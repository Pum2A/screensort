import {backendFetch} from '../desktop/transport.js'

export type Batch = {id: string, folderPath: string, status: 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED',
    total: number, completed: number, failed: number, processing: number, ocr: boolean, visual: boolean, createdAt: string}
export type BatchItem = {fileId: number, originalName: string, status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED',
    category: string | null, proposedName: string | null, message: string | null}
export type BatchPage = {items: BatchItem[], hasNext: boolean}

async function checked<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const messages: Record<number, string> = {400: 'Sprawdź folder i wybrane opcje.',
            409: 'Zadanie jest już w toku lub folder wymaga skanu. Poczekaj na bieżący obraz.',
            503: 'Uruchom pakiet AI i spróbuj ponownie.'}
        throw new Error(messages[response.status] ?? 'Nie udało się odczytać zadania. Spróbuj ponownie.')
    }
    return await response.json() as T
}
export async function latestBatch(folderPath: string, signal: AbortSignal): Promise<Batch | null> {
    const response = await backendFetch(`/api/organization/batches/latest?${new URLSearchParams({folderPath})}`, {signal})
    return response.status === 404 ? null : checked<Batch>(response)
}
export async function startBatch(folderPath: string, ocr: boolean, visual: boolean, signal: AbortSignal): Promise<Batch> {
    return checked<Batch>(await backendFetch('/api/organization/batches', {method: 'POST', signal,
        headers: {'Content-Type': 'application/json'}, body: JSON.stringify({folderPath, ocr, visual})}))
}
export async function readBatch(id: string, signal: AbortSignal): Promise<Batch> {
    return checked<Batch>(await backendFetch(`/api/organization/batches/${encodeURIComponent(id)}`, {signal}))
}
export async function batchItems(id: string, page: number, signal: AbortSignal): Promise<BatchPage> {
    return checked<BatchPage>(await backendFetch(`/api/organization/batches/${encodeURIComponent(id)}/items?page=${page}`, {signal}))
}
export async function controlBatch(id: string, action: 'PAUSE' | 'RESUME' | 'CANCEL', signal: AbortSignal): Promise<Batch> {
    return checked<Batch>(await backendFetch(`/api/organization/batches/${encodeURIComponent(id)}/control`, {
        method: 'POST', signal, headers: {'Content-Type': 'application/json'}, body: JSON.stringify({action})}))
}
