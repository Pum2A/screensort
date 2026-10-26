import {requestJson} from '../shared/http.js'
import type {ErrorMessages} from '../shared/http.js'
import {librarySchema} from './types.js'
import type {Library} from './types.js'

const messages: ErrorMessages = {
    byStatus: {400: 'Sprawdź folder. Biblioteka pokazuje foldery do 1000 obrazów.'},
    fallback: 'Nie udało się pobrać biblioteki. Sprawdź folder i działanie backendu.',
}

export const libraryKeys = {
    all: ['library'] as const,
    folder: (folderPath: string) => ['library', folderPath] as const,
}

export function fetchLibrary(folderPath: string, signal?: AbortSignal): Promise<Library> {
    return requestJson(`/api/library?${new URLSearchParams({folderPath})}`, librarySchema, messages, {signal})
}
