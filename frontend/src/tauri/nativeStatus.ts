import {invoke} from '@tauri-apps/api/core'
import {listen} from '@tauri-apps/api/event'
import type {UnlistenFn} from '@tauri-apps/api/event'

// The shell pushes every change as an event (src-tauri/src/desktop/events.rs). Polling stays only as a
// slow safety net, and quick after a failed read, so missed reads are still noticed and counted.
export const FALLBACK_POLL_MS = 15_000
export const RETRY_AFTER_FAILURE_MS = 1_000

/** Reads a native value now, then follows its change events. Returns a function that stops it. */
export function watchNative<T>(command: string, event: string, onValue: (value: T) => void,
    onError: () => void): () => void {
    let stopped = false
    let timer: ReturnType<typeof setTimeout> | undefined
    let unlisten: UnlistenFn | null = null
    async function check() {
        let delay = FALLBACK_POLL_MS
        try {
            const value = await invoke<T>(command)
            if (!stopped) onValue(value)
        } catch {
            delay = RETRY_AFTER_FAILURE_MS
            if (!stopped) onError()
        }
        if (!stopped) timer = setTimeout(() => void check(), delay)
    }
    listen<T>(event, message => { if (!stopped) onValue(message.payload) })
        .then(stop => { if (stopped) stop(); else unlisten = stop })
        // Without events the fallback poll still keeps the window up to date, only slower.
        .catch(() => {})
    void check()
    return () => { stopped = true; clearTimeout(timer); unlisten?.() }
}
