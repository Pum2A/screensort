import type {z} from 'zod'
import {backendFetch} from '../desktop/transport.js'

export type ErrorMessages = {
    byCode?: Readonly<Record<string, string>>
    byStatus?: Readonly<Record<number, string>>
    fallback: string
}

// Raw server bodies may hold SQL, paths or stack traces, so only mapped Polish messages reach the UI.
export class ApiError extends Error {
    readonly status: number
    readonly code: string | null

    constructor(message: string, status: number, code: string | null) {
        super(message)
        this.name = 'ApiError'
        this.status = status
        this.code = code
    }
}

export const INVALID_RESPONSE_MESSAGE = 'Serwer zwrócił nieoczekiwane dane. Odśwież widok i spróbuj ponownie.'

export type RequestOptions = {method?: string, body?: unknown, signal?: AbortSignal}

async function errorCode(response: Response): Promise<string | null> {
    const body: unknown = await response.json().catch(() => null)
    if (typeof body === 'object' && body !== null && 'code' in body && typeof body.code === 'string') return body.code
    return null
}

export async function toApiError(response: Response, messages: ErrorMessages): Promise<ApiError> {
    const code = await errorCode(response)
    const byCode = code === null ? undefined : messages.byCode?.[code]
    const message = byCode ?? messages.byStatus?.[response.status] ?? messages.fallback
    return new ApiError(message, response.status, code)
}

export async function parseJson<S extends z.ZodType>(response: Response, schema: S): Promise<z.infer<S>> {
    const parsed = schema.safeParse(await response.json().catch(() => undefined))
    if (!parsed.success) throw new ApiError(INVALID_RESPONSE_MESSAGE, response.status, 'INVALID_RESPONSE')
    return parsed.data
}

export function send(path: string, {method = 'GET', body, signal}: RequestOptions = {}): Promise<Response> {
    const init: RequestInit = {method, signal}
    if (body !== undefined) {
        init.headers = {'Content-Type': 'application/json'}
        init.body = JSON.stringify(body)
    }
    return backendFetch(path, init)
}

export async function requestJson<S extends z.ZodType>(
    path: string, schema: S, messages: ErrorMessages, options: RequestOptions = {},
): Promise<z.infer<S>> {
    const response = await send(path, options)
    if (!response.ok) throw await toApiError(response, messages)
    return parseJson(response, schema)
}

// For "latest X" endpoints, where 404 means "nothing yet" rather than an error.
export async function requestJsonOrNull<S extends z.ZodType>(
    path: string, schema: S, messages: ErrorMessages, options: RequestOptions = {},
): Promise<z.infer<S> | null> {
    const response = await send(path, options)
    if (response.status === 404) return null
    if (!response.ok) throw await toApiError(response, messages)
    return parseJson(response, schema)
}
