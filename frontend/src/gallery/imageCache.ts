// Coming back to a page shows images already fetched in this window without asking the backend.
// The address carries the file's content hash, so a changed file gets a new address and a new entry.
export const MAX_CACHED_IMAGES = 300
export const MAX_CACHED_BYTES = 64 * 1024 * 1024

/** Least recently used entries leave first. A Map keeps insertion order, so re-inserting marks "used". */
export function createImageCache(maxEntries: number, maxBytes: number) {
    const entries = new Map<string, Blob>()
    let usedBytes = 0

    return {
        get(key: string): Blob | undefined {
            const blob = entries.get(key)
            if (blob) { entries.delete(key); entries.set(key, blob) }
            return blob
        },
        set(key: string, blob: Blob) {
            if (blob.size > maxBytes) return
            const previous = entries.get(key)
            if (previous) { entries.delete(key); usedBytes -= previous.size }
            entries.set(key, blob)
            usedBytes += blob.size
            for (const [oldest, old] of entries) {
                if (entries.size <= maxEntries && usedBytes <= maxBytes) break
                entries.delete(oldest)
                usedBytes -= old.size
            }
        },
        get size() { return entries.size },
    }
}

export const imageCache = createImageCache(MAX_CACHED_IMAGES, MAX_CACHED_BYTES)
