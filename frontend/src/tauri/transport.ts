import {invoke, isTauri} from '@tauri-apps/api/core'

export function isDesktop(): boolean {
    return typeof window !== 'undefined' && isTauri()
}

/**
 * Tauri rejects a command with the plain string Rust returned, not an Error. Screens read
 * `error.message`, so a bare string looked like "no error" and left them loading forever.
 */
export function bridgeError(cause: unknown): Error {
    if (cause instanceof Error) return cause
    return new Error(typeof cause === 'string' && cause.trim() ? cause : 'Aplikacja nie mogła połączyć się z katalogiem.')
}

// Session credentials and backend ports stay in Rust, never localStorage or URLs.
export async function backendFetch(path: string, init: RequestInit = {}): Promise<Response> {
    if (!isDesktop()) return fetch(path, init)
    if (init.signal?.aborted) throw new DOMException('Aborted', 'AbortError')
    const result = await invoke<{status: number, body: unknown}>('backend_request', {
        method: init.method ?? 'GET', path,
        body: typeof init.body === 'string' ? JSON.parse(init.body) as unknown : null,
    }).catch((cause: unknown) => { throw bridgeError(cause) })
    if (init.signal?.aborted) throw new DOMException('Aborted', 'AbortError')
    return new Response(
        result.status === 204 ? null : typeof result.body === 'string' ? result.body : JSON.stringify(result.body),
        {status: result.status, headers: {'Content-Type': 'application/json'}},
    )
}

export async function desktopImage(path: string): Promise<Blob> {
    const bytes = await invoke<ArrayBuffer>('backend_image', {path})
    return new Blob([bytes], {type: 'image/png'})
}
