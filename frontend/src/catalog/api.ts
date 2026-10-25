import {requestJson} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'
import {catalogPageSchema} from './types.js'
import type {CatalogFile, CatalogPage} from './types.js'

const messages: ErrorMessages = {
    fallback: 'Nie udało się pobrać katalogu. Sprawdź folder i działanie backendu.',
}

export function fetchCatalog(folderPath: string, pageNumber: number, pageSize: number, signal?: AbortSignal): Promise<CatalogPage> {
    const query = new URLSearchParams({folderPath, pageNumber: pageNumber.toString(), pageSize: pageSize.toString()})
    return requestJson(`/api/catalog/page?${query}`, catalogPageSchema, messages, {signal})
}

export function imageUrl(file: CatalogFile, size: 'thumbnail' | 'preview'): string {
    // ID wskazuje rekord. Nie przekazujemy endpointowi ścieżki do otwarcia.
    // Wersja to treść pliku, nie data skanu: ponowny skan nie zmienia adresu, więc cache trafia.
    // Ten sam adres buduje przegląd organizacji (reviewImageUrl), więc oba widoki dzielą wpis.
    const version = new URLSearchParams({v: file.sha256})
    return `/api/catalog/files/${file.id}/${size}?${version}`
}
