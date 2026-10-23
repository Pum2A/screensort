// The backend decodes two previews at a time. Four requests in flight keep it busy while the
// rest wait here, where leaving the page or scrolling away can still cancel them.
export const MAX_THUMBNAILS_IN_FLIGHT = 4
export const THUMBNAIL_ATTEMPTS = 3
export const RETRY_DELAY_MS = 1000

function aborted(signal: AbortSignal): DOMException {
    return signal.reason instanceof DOMException ? signal.reason : new DOMException('Aborted', 'AbortError')
}

export function wait(milliseconds: number, signal: AbortSignal): Promise<void> {
    if (signal.aborted) return Promise.reject(aborted(signal))
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => { signal.removeEventListener('abort', stop); resolve() }, milliseconds)
        function stop() { clearTimeout(timer); reject(aborted(signal)) }
        signal.addEventListener('abort', stop, {once: true})
    })
}

/** Runs at most `limit` tasks at once; the others start in arrival order as slots free up. */
export function createLimiter(limit: number) {
    let active = 0
    const queue: Array<() => void> = []

    function release() {
        const next = queue.shift()
        if (next) next()
        else active--
    }

    async function acquire(signal: AbortSignal): Promise<void> {
        if (signal.aborted) throw aborted(signal)
        if (active < limit) { active++; return }
        await new Promise<void>((resolve, reject) => {
            // The slot is handed over directly, so `active` does not change here.
            const start = () => { signal.removeEventListener('abort', leave); resolve() }
            function leave() {
                const index = queue.indexOf(start)
                if (index >= 0) queue.splice(index, 1)
                reject(aborted(signal))
            }
            queue.push(start)
            signal.addEventListener('abort', leave, {once: true})
        })
    }

    return {
        async run<T>(task: () => Promise<T>, signal: AbortSignal): Promise<T> {
            await acquire(signal)
            try { return await task() }
            finally { release() }
        },
        get waiting() { return queue.length },
    }
}

/** Retries a failed load with growing pauses (1 s, 2 s, ...). Cancelling stops the retries. */
export async function withRetry<T>(
    task: () => Promise<T>, signal: AbortSignal,
    {attempts = THUMBNAIL_ATTEMPTS, delayMs = RETRY_DELAY_MS, pause = wait} = {},
): Promise<T> {
    for (let attempt = 1; ; attempt++) {
        try {
            return await task()
        } catch (cause) {
            if (signal.aborted) throw aborted(signal)
            if (attempt >= attempts) throw cause
            await pause(delayMs * 2 ** (attempt - 1), signal)
        }
    }
}

export const thumbnailSlots = createLimiter(MAX_THUMBNAILS_IN_FLIGHT)
