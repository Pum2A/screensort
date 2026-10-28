import {z} from 'zod'
import {requestJson, requestJsonOrNull} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'

// Mirrors ExportStore.Job / ExportStore.Issues in Java; statuses are fixed by the V8/V9 CHECK constraints.
export const exportJobSchema = z.object({
    id: z.string(),
    batchId: z.string(),
    status: z.enum(['RUNNING', 'COMPLETED', 'INTERRUPTED']),
    outputFolder: z.string(),
    total: z.number().int(),
    copied: z.number().int(),
    failed: z.number().int(),
    message: z.string().nullable(),
    createdAt: z.string(),
})

export const exportIssuesSchema = z.object({
    items: z.array(z.object({originalName: z.string(), message: z.string()})),
    hasNext: z.boolean(),
})

export type ExportJob = z.infer<typeof exportJobSchema>
export type ExportIssues = z.infer<typeof exportIssuesSchema>

const messages: ErrorMessages = {
    byCode: {
        NO_APPROVALS: 'Najpierw zatwierdź aktualne nazwy i kategorie obrazów.',
        EXPORT_BUSY: 'Kopiowanie jest już w toku. Poczekaj na zakończenie.',
        BATCH_RUNNING: 'Poczekaj na zakończenie analizy folderu.',
        DESTINATION_REQUIRED: 'Wybierz folder dla uporządkowanej kopii.',
        DESTINATION_UNAVAILABLE: 'Nie można zapisywać w wybranym folderze. Wybierz inne miejsce.',
        EXPORT_NOT_FOUND: 'Nie znaleziono zadania kopiowania.',
    },
    fallback: 'Nie udało się obsłużyć kopiowania. Spróbuj ponownie.',
}

export const exportKeys = {
    latest: (batchId: string) => ['export', 'latest', batchId] as const,
    job: (id: string) => ['export', 'job', id] as const,
    issues: (id: string, page: number) => ['export', 'job', id, 'issues', page] as const,
}

export function startExport(batchId: string, destinationFolder: string): Promise<ExportJob> {
    return requestJson(`/api/organization/batches/${encodeURIComponent(batchId)}/exports`, exportJobSchema, messages,
        {method: 'POST', body: {destinationFolder}})
}

export function latestExport(batchId: string, signal?: AbortSignal): Promise<ExportJob | null> {
    return requestJsonOrNull(`/api/organization/batches/${encodeURIComponent(batchId)}/exports/latest`, exportJobSchema, messages, {signal})
}

export function readExport(id: string, signal?: AbortSignal): Promise<ExportJob> {
    return requestJson(`/api/organization/exports/${encodeURIComponent(id)}`, exportJobSchema, messages, {signal})
}

export function exportIssues(id: string, page: number, signal?: AbortSignal): Promise<ExportIssues> {
    return requestJson(`/api/organization/exports/${encodeURIComponent(id)}/issues?page=${page}`, exportIssuesSchema, messages, {signal})
}
