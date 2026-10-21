import {backendFetch} from '../../desktop/transport.js'

export type ExportJob = {id: string; batchId: string; status: 'RUNNING' | 'COMPLETED' | 'INTERRUPTED';
    outputFolder: string; total: number; copied: number; failed: number; message: string | null; createdAt: string}
export type ExportIssues = {items: {originalName: string; message: string}[]; hasNext: boolean}

async function checked<T>(response: Response): Promise<T> {
    if (!response.ok) {
        let code = ''
        try {code = (await response.json() as {code?: string}).code ?? ''} catch { /* Unknown responses use a fixed message. */ }
        const messages: Record<string, string> = {
            NO_APPROVALS: 'Najpierw zatwierdź aktualne nazwy i kategorie obrazów.',
            EXPORT_BUSY: 'Kopiowanie jest już w toku. Poczekaj na zakończenie.',
            BATCH_RUNNING: 'Poczekaj na zakończenie analizy folderu.',
            DESTINATION_REQUIRED: 'Wybierz folder dla uporządkowanej kopii.',
            DESTINATION_UNAVAILABLE: 'Nie można zapisywać w wybranym folderze. Wybierz inne miejsce.',
            EXPORT_NOT_FOUND: 'Nie znaleziono zadania kopiowania.',
        }
        throw new Error(messages[code] ?? 'Nie udało się obsłużyć kopiowania. Spróbuj ponownie.')
    }
    return await response.json() as T
}

export async function startExport(batchId: string, destinationFolder: string): Promise<ExportJob> {
    return checked<ExportJob>(await backendFetch(`/api/organization/batches/${encodeURIComponent(batchId)}/exports`, {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({destinationFolder}),
    }))
}

export async function latestExport(batchId: string, signal: AbortSignal): Promise<ExportJob | null> {
    const response = await backendFetch(`/api/organization/batches/${encodeURIComponent(batchId)}/exports/latest`, {signal})
    return response.status === 404 ? null : checked<ExportJob>(response)
}

export async function readExport(id: string, signal: AbortSignal): Promise<ExportJob> {
    return checked<ExportJob>(await backendFetch(`/api/organization/exports/${encodeURIComponent(id)}`, {signal}))
}

export async function exportIssues(id: string, page: number, signal: AbortSignal): Promise<ExportIssues> {
    return checked<ExportIssues>(await backendFetch(`/api/organization/exports/${encodeURIComponent(id)}/issues?page=${page}`, {signal}))
}
