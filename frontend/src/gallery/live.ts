import type {EntryState, LibraryEntry} from './model.js'

export type FeedEvent = {fileId: number, kind: 'named' | 'failed', detail: string, at: number}

const waiting: ReadonlySet<EntryState> = new Set(['queued', 'fresh'])

/** What changed between two polls: a tile that got a proposal or failed. Decisions are not news. */
export function feedEvents(before: ReadonlyMap<number, EntryState>, entries: LibraryEntry[], at: number): FeedEvent[] {
    const events: FeedEvent[] = []
    for (const entry of entries) {
        const was = before.get(entry.file.id)
        if (was === undefined || !waiting.has(was) || waiting.has(entry.state)) continue
        if (entry.state === 'failed') events.push({fileId: entry.file.id, kind: 'failed', detail: entry.file.originalName, at})
        else if (entry.state === 'sure' || entry.state === 'check') {
            events.push({fileId: entry.file.id, kind: 'named', detail: entry.item?.proposedName ?? entry.file.originalName, at})
        }
    }
    return events
}

/** "ok. 1:40" from the pace seen since the view opened; nothing until there is a pace to show. */
export function etaLabel(doneSinceStart: number, elapsedMs: number, remaining: number): string | null {
    if (doneSinceStart < 2 || elapsedMs < 3000 || remaining <= 0) return null
    const seconds = Math.round(elapsedMs / doneSinceStart * remaining / 1000)
    const minutes = Math.floor(seconds / 60)
    return minutes >= 60 ? `ok. ${Math.floor(minutes / 60)} h ${minutes % 60} min` : `ok. ${minutes}:${String(seconds % 60).padStart(2, '0')}`
}
