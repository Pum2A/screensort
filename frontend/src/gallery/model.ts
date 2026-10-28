import type {CatalogFile} from './catalogTypes.js'
import type {BatchItem} from '../organization/types.js'
import {effectiveCategory} from '../review/filters.js'
import {categoryLabels, draftFor, isCategory, reviewWrite} from '../review/state.js'
import type {ImageCategory, ReviewWrite} from '../review/state.js'
import type {Library} from './libraryTypes.js'

/**
 * Where a file stands on the way to the organized copy:
 * approved/skipped = the owner decided; sure/check = analysed and waiting for a decision
 * (sure: category and name without doubts); queued/failed = in the analysis; fresh = never analysed
 * or changed since the analysis.
 */
export type EntryState = 'approved' | 'skipped' | 'sure' | 'check' | 'queued' | 'failed' | 'fresh'
export type LibraryEntry = {file: CatalogFile, item: BatchItem | null, state: EntryState,
    category: ImageCategory | null, copies: number}
export type LibraryFilter = 'all' | 'pending' | ImageCategory
export type LibrarySort = 'newest' | 'name'
export type DuplicateGroup = {sha256: string, files: CatalogFile[], extraBytes: number}

export const categories = Object.keys(categoryLabels) as ImageCategory[]

// Instants arrive with a varying number of fraction digits, so they are compared as numbers, not text.
const time = (instant: string) => Date.parse(instant)

export function entryState(item: BatchItem | null): EntryState {
    if (!item) return 'fresh'
    if (item.review?.matchesCatalog) return item.review.decision === 'APPROVED' ? 'approved' : 'skipped'
    if (item.status === 'FAILED') return 'failed'
    if (item.status !== 'COMPLETED') return 'queued'
    if (!item.analysisMatchesCatalog || item.analysisId === null) return 'fresh'
    return isCategory(item.category) && item.proposedName && !item.proposalUncertain ? 'sure' : 'check'
}

export function isPending(state: EntryState): boolean {
    return state === 'sure' || state === 'check'
}

export function libraryEntries(library: Library): LibraryEntry[] {
    const items = new Map(library.items.map(item => [item.fileId, item]))
    const copies = new Map<string, number>()
    for (const file of library.files) copies.set(file.sha256, (copies.get(file.sha256) ?? 0) + 1)
    return library.files.map(file => {
        const item = items.get(file.id) ?? null
        const state = entryState(item)
        const category = item && state !== 'fresh' ? effectiveCategory(item) : null
        return {file, item, state, category: isCategory(category) ? category : null, copies: copies.get(file.sha256) ?? 1}
    })
}

export function filterEntries(entries: LibraryEntry[], filter: LibraryFilter, query: string): LibraryEntry[] {
    const search = query.trim().normalize('NFKC').toLocaleLowerCase('pl-PL')
    return entries.filter(entry => (filter === 'all' || (filter === 'pending' ? isPending(entry.state) : entry.category === filter))
        && (!search || entry.file.originalName.normalize('NFKC').toLocaleLowerCase('pl-PL').includes(search)))
}

export function sortEntries(entries: LibraryEntry[], sort: LibrarySort): LibraryEntry[] {
    return entries.toSorted(sort === 'name'
        ? (a, b) => a.file.originalName.localeCompare(b.file.originalName, 'pl-PL', {numeric: true})
        : (a, b) => time(b.file.lastModifiedAt) - time(a.file.lastModifiedAt) || a.file.id - b.file.id)
}

export type LibraryCounts = Record<EntryState, number> & {pending: number,
    byCategory: Record<ImageCategory, {total: number, pending: number}>}

export function countEntries(entries: LibraryEntry[]): LibraryCounts {
    const counts: LibraryCounts = {approved: 0, skipped: 0, sure: 0, check: 0, queued: 0, failed: 0, fresh: 0, pending: 0,
        byCategory: Object.fromEntries(categories.map(category => [category, {total: 0, pending: 0}])) as LibraryCounts['byCategory']}
    for (const entry of entries) {
        counts[entry.state] += 1
        if (isPending(entry.state)) counts.pending += 1
        if (entry.category) {
            counts.byCategory[entry.category].total += 1
            if (isPending(entry.state)) counts.byCategory[entry.category].pending += 1
        }
    }
    return counts
}

/** Byte-identical files (same SHA-256). The oldest copy goes first: it is the one kept by default. */
export function duplicateGroups(files: CatalogFile[]): DuplicateGroup[] {
    const groups = new Map<string, CatalogFile[]>()
    for (const file of files) groups.set(file.sha256, [...groups.get(file.sha256) ?? [], file])
    return [...groups].filter(([, group]) => group.length > 1)
        .map(([sha256, group]) => {
            const sorted = group.toSorted((a, b) => time(a.lastModifiedAt) - time(b.lastModifiedAt) || a.id - b.id)
            return {sha256, files: sorted, extraBytes: sorted.slice(1).reduce((sum, file) => sum + file.sizeBytes, 0)}
        })
        .toSorted((a, b) => b.extraBytes - a.extraBytes || a.files[0].originalName.localeCompare(b.files[0].originalName))
}

/** Shift+click: everything between the last clicked tile and this one joins the selection. */
export function selectRange(order: number[], anchor: number | null, target: number, selected: ReadonlySet<number>): Set<number> {
    const next = new Set(selected)
    const from = anchor === null ? -1 : order.indexOf(anchor)
    const to = order.indexOf(target)
    if (from < 0 || to < 0) { next.add(target); return next }
    for (const id of order.slice(Math.min(from, to), Math.max(from, to) + 1)) next.add(id)
    return next
}

/** category applies to every file; overrides are per-file picks (the review cards), used when category is absent. */
export type BulkAction = {decision: 'APPROVED', category?: ImageCategory, overrides?: ReadonlyMap<number, ImageCategory>}
    | {decision: 'SKIPPED'}
export type BulkPlan = {writes: {fileId: number, write: ReviewWrite}[], blocked: number}

/**
 * The same review write the single-image editor sends, one per file. A file without a current
 * analysis, approved without any category or without a certain name, is counted as blocked instead of guessed.
 */
export function bulkPlan(entries: LibraryEntry[], action: BulkAction): BulkPlan {
    const writes: BulkPlan['writes'] = []
    let blocked = 0
    for (const entry of entries) {
        const item = entry.item
        if (!item || item.analysisId === null || entry.state === 'fresh' || entry.state === 'queued' || entry.state === 'failed') { blocked += 1; continue }
        const draft = draftFor(item)
        if (action.decision === 'APPROVED') {
            // Bulk approval never signs off a doubtful or missing name: those need a look in the editor.
            if (item.approvedName === null && (!item.proposedName || item.proposalUncertain)) { blocked += 1; continue }
            const category = action.category ?? action.overrides?.get(item.fileId) ?? (isCategory(draft.category) ? draft.category : null)
            if (!category) { blocked += 1; continue }
            if (entry.state === 'approved' && entry.category === category) continue
            writes.push({fileId: item.fileId, write: reviewWrite({...draft, category}, 'APPROVED')})
        } else if (entry.state !== 'skipped') {
            writes.push({fileId: item.fileId, write: reviewWrite(draft, 'SKIPPED')})
        }
    }
    return {writes, blocked}
}

export function todayCount(files: CatalogFile[], now = new Date()): number {
    return files.filter(file => new Date(file.firstDiscoveredAt).toDateString() === now.toDateString()).length
}

export function lastScan(files: CatalogFile[]): string | null {
    return files.reduce<string | null>((latest, file) => latest === null || time(file.lastSeenAt) > time(latest) ? file.lastSeenAt : latest, null)
}

export type ReviewTab = 'pending' | 'sure' | 'check' | 'approved' | 'skipped' | 'failed' | 'queued'

/**
 * "pending" is everything analysed and still waiting for the owner; "queued" is not analysed yet
 * (in the queue, or changed since the analysis); the other tabs are single states.
 */
export function inReviewTab(entry: LibraryEntry, tab: ReviewTab): boolean {
    if (tab === 'pending') return isPending(entry.state)
    if (tab === 'queued') return entry.state === 'queued' || entry.state === 'fresh'
    return entry.state === tab
}
