import {ApiError, requestJson} from '../../shared/http.js'
import type {ErrorMessages, RequestOptions} from '../../shared/http.js'
import {batchItemSchema} from '../types.js'
import type {BatchItem} from '../types.js'
import type {ReviewWrite} from './state.js'

const messages: ErrorMessages = {
    byCode: {
        INVALID_REQUEST: 'Formularz jest niekompletny lub nieaktualny. Wczytaj aktualny wynik.',
        INVALID_CATEGORY: 'Wybierz kategorię obrazu. „Do sprawdzenia” nie jest kategorią docelową.',
        INVALID_NAME: 'Popraw nazwę: zachowaj rozszerzenie i usuń niedozwolone znaki.',
        ITEM_NOT_FOUND: 'Plik nie należy do tej analizy folderu.',
        FILE_NOT_FOUND: 'Plik nie istnieje już w katalogu.',
        ITEM_NOT_READY: 'Poczekaj na zakończenie analizy tego obrazu.',
        SOURCE_CHANGED: 'Plik zmienił się w katalogu. Zeskanuj i przeanalizuj go ponownie.',
        ANALYSIS_OUTDATED: 'Wykonaj analizę aktualnej wersji obrazu.',
        ANALYSIS_CHANGED: 'Zapisana analiza się zmieniła. Odśwież wynik; może być potrzebna ponowna analiza folderu.',
        ANALYSIS_REQUIRED: 'Najpierw przeanalizuj obraz.',
        REVIEW_CHANGED: 'Zapisano inną nazwę lub decyzję. Twoja edycja została zachowana — wczytaj aktualną wersję przed zapisem.',
    },
    fallback: 'Nie udało się zapisać decyzji. Spróbuj ponownie.',
}

const reviewPath = (batchId: string, fileId: number) =>
    `/api/organization/batches/${encodeURIComponent(batchId)}/items/${fileId}/review`

// A valid response for a different image must never overwrite this form.
async function forFile(fileId: number, path: string, options: RequestOptions): Promise<BatchItem> {
    const item = await requestJson(path, batchItemSchema, messages, options)
    if (item.fileId !== fileId) throw new ApiError('Odpowiedź dotyczy innego obrazu. Odśwież formularz.', 200, 'WRONG_ITEM')
    return item
}

export function readReview(batchId: string, fileId: number, signal?: AbortSignal): Promise<BatchItem> {
    return forFile(fileId, reviewPath(batchId, fileId), {signal})
}

export function saveReview(batchId: string, fileId: number, request: ReviewWrite, signal?: AbortSignal): Promise<BatchItem> {
    return forFile(fileId, reviewPath(batchId, fileId), {method: 'PUT', signal, body: request})
}
