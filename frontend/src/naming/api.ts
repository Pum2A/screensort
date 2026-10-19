import {backendFetch} from '../desktop/transport.js'
import type {NamingReview} from './types.js'

const messages: Record<string, string> = {
    ANALYSIS_REQUIRED: 'Najpierw wykonaj analizę obrazu.',
    ANALYSIS_OUTDATED: 'Wykonaj analizę aktualnej wersji obrazu.',
    ANALYSIS_CHANGED: 'Analiza się zmieniła. Odśwież propozycję przed zatwierdzeniem.',
    REVIEW_CHANGED: 'Zapisano już inną decyzję. Wczytaj ją przed kolejną zmianą.',
    INVALID_NAME: 'Nazwa jest nieprawidłowa. Sprawdź znaki, długość i rozszerzenie.',
    INVALID_REQUEST: 'Dane formularza są nieaktualne lub nieprawidłowe. Odśwież propozycję.',
    FILE_NOT_FOUND: 'Plik nie istnieje już w katalogu.',
}

async function checked(response: Response): Promise<NamingReview> {
    if (!response.ok) {
        const error = await response.json().catch(() => null) as {code?: string} | null
        throw new Error(error?.code && messages[error.code]
            ? messages[error.code] : 'Nie udało się odczytać lub zapisać decyzji. Spróbuj ponownie.')
    }
    return await response.json() as NamingReview
}

export async function readNaming(fileId: number, signal: AbortSignal): Promise<NamingReview> {
    return checked(await backendFetch(`/api/catalog/files/${fileId}/naming`, {signal}))
}

export async function approveNaming(review: NamingReview, name: string, signal: AbortSignal): Promise<NamingReview> {
    return checked(await backendFetch(`/api/catalog/files/${review.fileId}/naming`, {
        method: 'PUT', signal, headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({analysisId: review.analysisId, sourceSha256: review.sourceSha256,
            revision: review.revision, name}),
    }))
}
