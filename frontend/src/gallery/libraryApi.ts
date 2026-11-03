import {z} from 'zod'
import {requestJson} from '../http/http.js'
import type {ErrorMessages} from '../http/http.js'
import {librarySchema} from './libraryTypes.js'
import type {Library} from './libraryTypes.js'

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

const textHitSchema = z.object({fileId: z.number().int(), source: z.enum(['TEXT', 'AI']),
    before: z.string(), match: z.string(), after: z.string()})
const searchSchema = z.object({hits: z.array(textHitSchema)})
export type TextHit = z.infer<typeof textHitSchema>

/** Images of the folder whose text or AI description contains the query (at least two characters). */
export function searchLibrary(folderPath: string, q: string, signal?: AbortSignal): Promise<{hits: TextHit[]}> {
    return requestJson(`/api/library/search?${new URLSearchParams({folderPath, q})}`, searchSchema, messages, {signal})
}
