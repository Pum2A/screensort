import {requestJson} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'
import {catalogPageSchema} from './types.js'
import type {CatalogFile, CatalogPage} from './types.js'

const messages: ErrorMessages = {
    fallback: 'Nie udało się pobrać katalogu. Sprawdź folder i działanie backendu.',
}

export function fetchCatalog(folderPath: string, pageNumber: number, pageSize: number, search = '', signal?: AbortSignal): Promise<CatalogPage> {
    const query = new URLSearchParams({folderPath, pageNumber: pageNumber.toString(), pageSize: pageSize.toString()})
    // The backend searches the whole folder; an empty search lists every file.
    if (search.trim()) query.set('search', search.trim())
    return requestJson(`/api/catalog/page?${query}`, catalogPageSchema, messages, {signal})
}

export function imageUrl(file: CatalogFile, size: 'thumbnail' | 'preview'): string {
    // ID wskazuje rekord. Nie przekazujemy endpointowi ścieżki do otwarcia.
    const version = new URLSearchParams({v: `${file.lastSeenAt}:${file.lastModifiedAt}`})
    return `/api/catalog/files/${file.id}/${size}?${version}`
}
