import {requestJson} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'
import {namingReviewSchema} from './types.js'
import type {NamingReview} from './types.js'

const messages: ErrorMessages = {
    byCode: {
        ANALYSIS_REQUIRED: 'Najpierw wykonaj analizę obrazu.',
        ANALYSIS_OUTDATED: 'Wykonaj analizę aktualnej wersji obrazu.',
        ANALYSIS_CHANGED: 'Analiza się zmieniła. Odśwież propozycję przed zatwierdzeniem.',
        REVIEW_CHANGED: 'Zapisano już inną decyzję. Wczytaj ją przed kolejną zmianą.',
        INVALID_NAME: 'Nazwa jest nieprawidłowa. Sprawdź znaki, długość i rozszerzenie.',
        INVALID_REQUEST: 'Dane formularza są nieaktualne lub nieprawidłowe. Odśwież propozycję.',
        FILE_NOT_FOUND: 'Plik nie istnieje już w katalogu.',
    },
    fallback: 'Nie udało się odczytać lub zapisać decyzji. Spróbuj ponownie.',
}

// Query keys name cached data; every reader and writer of the same data uses the same key.
export const namingKeys = {
    review: (fileId: number) => ['naming', fileId] as const,
}

export function readNaming(fileId: number, signal?: AbortSignal): Promise<NamingReview> {
    return requestJson(`/api/catalog/files/${fileId}/naming`, namingReviewSchema, messages, {signal})
}

export function approveNaming(review: NamingReview, name: string, signal?: AbortSignal): Promise<NamingReview> {
    return requestJson(`/api/catalog/files/${review.fileId}/naming`, namingReviewSchema, messages, {
        method: 'PUT', signal,
        body: {analysisId: review.analysisId, sourceSha256: review.sourceSha256, revision: review.revision, name},
    })
}
