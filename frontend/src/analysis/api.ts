import {requestJson, requestJsonOrNull} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'
import {analysisJobSchema, savedAnalysisSchema} from './types.js'
import type {AnalysisJob, SavedAnalysis} from './types.js'

const messages: ErrorMessages = {
    byStatus: {
        400: 'Wybierz obraz i co najmniej jeden rodzaj analizy.',
        409: 'Inna analiza jest już w toku. Poczekaj na jej zakończenie.',
        503: 'Silnik analizy jest zajęty lub niedostępny. Spróbuj ponownie później.',
    },
    fallback: 'Nie udało się pobrać poprawnej odpowiedzi analizy.',
}

export const analysisKeys = {
    latest: (fileId: number) => ['analysis', 'latest', fileId] as const,
    job: (id: string) => ['analysis', 'job', id] as const,
}

export function startAnalysis(fileId: number, ocr: boolean, visual: boolean, signal?: AbortSignal): Promise<AnalysisJob> {
    return requestJson('/api/analysis/jobs', analysisJobSchema, messages, {method: 'POST', signal, body: {fileId, ocr, visual}})
}

export function findAnalysisJob(id: string, signal?: AbortSignal): Promise<AnalysisJob | null> {
    return requestJsonOrNull(`/api/analysis/jobs/${encodeURIComponent(id)}`, analysisJobSchema, messages, {signal})
}

export function latestAnalysis(fileId: number, signal?: AbortSignal): Promise<SavedAnalysis | null> {
    return requestJsonOrNull(`/api/catalog/files/${fileId}/analysis`, savedAnalysisSchema, messages, {signal})
}
