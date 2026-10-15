import {backendFetch} from '../desktop/transport.js'
import type {AnalysisJob, SavedAnalysis} from './types.js'

async function checked<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const messages: Record<number, string> = {
            400: 'Wybierz obraz i co najmniej jeden rodzaj analizy.',
            409: 'Inna analiza jest już w toku. Poczekaj na jej zakończenie.',
            503: 'Silnik analizy jest zajęty lub niedostępny. Spróbuj ponownie później.',
        }
        throw new Error(messages[response.status] ?? 'Nie udało się pobrać poprawnej odpowiedzi analizy.')
    }
    return await response.json() as T
}

export async function startAnalysis(fileId: number, ocr: boolean, visual: boolean, signal: AbortSignal): Promise<AnalysisJob> {
    return checked<AnalysisJob>(await backendFetch('/api/analysis/jobs', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, signal,
        body: JSON.stringify({fileId, ocr, visual}),
    }))
}

export async function findAnalysisJob(id: string, signal: AbortSignal): Promise<AnalysisJob | null> {
    const response = await backendFetch(`/api/analysis/jobs/${encodeURIComponent(id)}`, {signal})
    if (response.status === 404) return null
    return checked<AnalysisJob>(response)
}

export async function latestAnalysis(fileId: number, signal: AbortSignal): Promise<SavedAnalysis | null> {
    const response = await backendFetch(`/api/catalog/files/${fileId}/analysis`, {signal})
    if (response.status === 404) return null
    return checked<SavedAnalysis>(response)
}
