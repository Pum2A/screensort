import {useMutation, useQueryClient} from '@tanstack/react-query'
import {saveReview, withdrawReview} from '../review/api.js'
import {libraryKeys} from './libraryApi.js'
import {bulkPlan} from './model.js'
import type {BulkAction, LibraryEntry} from './model.js'

/** One saved decision that "Cofnij" can take back: the image was waiting for a decision before. */
export type UndoStep = {fileId: number, revision: number}
export type BulkResult = {saved: number, savedIds: number[], failed: number, blocked: number, error: string | null, undo: UndoStep[]}

// A few writes at once: each one locks its file row, and the local backend has a small pool.
const PARALLEL_WRITES = 3

async function runBulk(batchId: string, entries: LibraryEntry[], action: BulkAction): Promise<BulkResult> {
    const {writes, blocked} = bulkPlan(entries, action)
    const waiting = new Set(entries.filter(entry => entry.state === 'sure' || entry.state === 'check').map(entry => entry.file.id))
    let failed = 0, error: string | null = null
    const savedIds: number[] = []
    const undo: UndoStep[] = []
    const queue = [...writes]
    async function worker() {
        for (let next = queue.shift(); next; next = queue.shift()) {
            try {
                const saved = await saveReview(batchId, next.fileId, next.write)
                savedIds.push(next.fileId)
                if (waiting.has(next.fileId) && saved.review) undo.push({fileId: next.fileId, revision: saved.review.revision})
            }
            catch (cause) { failed += 1; error ??= cause instanceof Error ? cause.message : 'Nie udało się zapisać decyzji.' }
        }
    }
    await Promise.all(Array.from({length: Math.min(PARALLEL_WRITES, writes.length)}, worker))
    return {saved: savedIds.length, savedIds, failed, blocked, error, undo}
}

/** Saves one decision for many files: the library bulk bar and the duplicates screen. */
export function useBulkReview(batchId: string | null) {
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: ({entries, action}: {entries: LibraryEntry[], action: BulkAction}) => {
            if (!batchId) return Promise.reject(new Error('Najpierw przeanalizuj folder.'))
            return runBulk(batchId, entries, action)
        },
        // Partial success still changed files, so both views reload either way.
        onSettled: () => Promise.all([
            queryClient.invalidateQueries({queryKey: libraryKeys.all}),
            queryClient.invalidateQueries({queryKey: ['organization']}),
        ]),
    })
}

/** Takes back the decisions of the last bulk action; one already changed elsewhere stays as it is. */
export function useUndoReview(batchId: string | null) {
    const queryClient = useQueryClient()
    return useMutation({
        mutationFn: async (steps: UndoStep[]) => {
            if (!batchId) return 0
            const results = await Promise.allSettled(steps.map(step => withdrawReview(batchId, step.fileId, step.revision)))
            return results.filter(result => result.status === 'rejected').length
        },
        onSettled: () => Promise.all([
            queryClient.invalidateQueries({queryKey: libraryKeys.all}),
            queryClient.invalidateQueries({queryKey: ['organization']}),
        ]),
    })
}

/** "Zatwierdzono 3 nazwy · kategoria Gry", "Pominięto 1 obraz": the text of the undo toast. */
export function decidedMessage(action: BulkAction, count: number, categoryLabel: string | null): string {
    const tens = count % 100, ones = count % 10
    const few = ones >= 2 && ones <= 4 && (tens < 12 || tens > 14)
    if (action.decision === 'SKIPPED') return `Pominięto ${count} ${count === 1 ? 'obraz' : few ? 'obrazy' : 'obrazów'}`
    const names = count === 1 ? 'nazwę' : few ? 'nazwy' : 'nazw'
    return `Zatwierdzono ${count} ${names}${categoryLabel ? ` · kategoria ${categoryLabel}` : ''}`
}

export function bulkMessage(result: BulkResult): string {
    const parts = [`Zapisano ${result.saved}`]
    if (result.blocked) parts.push(`${result.blocked} do sprawdzenia w edytorze (brak analizy, kategorii lub pewnej nazwy)`)
    if (result.failed) parts.push(`${result.failed} nie udało się${result.error ? `: ${result.error}` : ''}`)
    return parts.join(' · ')
}
