import {backendFetch} from '../../desktop/transport.js'
import type {BatchItem} from '../api.js'
import type {ReviewWrite} from './state.js'

const messages: Record<string, string> = {
    INVALID_REQUEST: 'Formularz jest niekompletny lub nieaktualny. Wczytaj aktualny wynik.',
    INVALID_CATEGORY: 'Wybierz kategorię obrazu. „Do sprawdzenia” nie jest kategorią docelową.',
    INVALID_NAME: 'Popraw nazwę: zachowaj rozszerzenie i usuń niedozwolone znaki.',
    ITEM_NOT_FOUND: 'Plik nie należy do tej analizy folderu.', FILE_NOT_FOUND: 'Plik nie istnieje już w katalogu.',
    ITEM_NOT_READY: 'Poczekaj na zakończenie analizy tego obrazu.',
    SOURCE_CHANGED: 'Plik zmienił się w katalogu. Zeskanuj i przeanalizuj go ponownie.',
    ANALYSIS_OUTDATED: 'Wykonaj analizę aktualnej wersji obrazu.',
    ANALYSIS_CHANGED: 'Zapisana analiza się zmieniła. Odśwież wynik; może być potrzebna ponowna analiza folderu.',
    ANALYSIS_REQUIRED: 'Najpierw przeanalizuj obraz.',
    REVIEW_CHANGED: 'Zapisano inną nazwę lub decyzję. Twoja edycja została zachowana — wczytaj aktualną wersję przed zapisem.',
}

async function checked(response: Response, fileId: number): Promise<BatchItem> {
    if (!response.ok) {
        const error = await response.json().catch(() => null) as {code?: string} | null
        throw new Error(error?.code && messages[error.code] ? messages[error.code] : 'Nie udało się zapisać decyzji. Spróbuj ponownie.')
    }
    const result = await response.json() as BatchItem
    if (result.fileId !== fileId) throw new Error('Odpowiedź dotyczy innego obrazu. Odśwież formularz.')
    return result
}

export async function readReview(batchId: string, fileId: number, signal: AbortSignal): Promise<BatchItem> {
    return checked(await backendFetch(`/api/organization/batches/${encodeURIComponent(batchId)}/items/${fileId}/review`, {signal}), fileId)
}

export async function saveReview(batchId: string, fileId: number, request: ReviewWrite, signal: AbortSignal): Promise<BatchItem> {
    return checked(await backendFetch(`/api/organization/batches/${encodeURIComponent(batchId)}/items/${fileId}/review`, {
        method: 'PUT', signal, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(request)}), fileId)
}
