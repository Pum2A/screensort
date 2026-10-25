import {useMutation, useQueryClient} from '@tanstack/react-query'
import {saveReview} from '../organization/review/api.js'
import {libraryKeys} from './api.js'
import {bulkPlan} from './model.js'
import type {BulkAction, LibraryEntry} from './model.js'

export type BulkResult = {saved: number, failed: number, blocked: number, error: string | null}

// A few writes at once: each one locks its file row, and the local backend has a small pool.
const PARALLEL_WRITES = 3

async function runBulk(batchId: string, entries: LibraryEntry[], action: BulkAction): Promise<BulkResult> {
    const {writes, blocked} = bulkPlan(entries, action)
    let saved = 0, failed = 0, error: string | null = null
    const queue = [...writes]
    async function worker() {
        for (let next = queue.shift(); next; next = queue.shift()) {
            try { await saveReview(batchId, next.fileId, next.write); saved += 1 }
            catch (cause) { failed += 1; error ??= cause instanceof Error ? cause.message : 'Nie udało się zapisać decyzji.' }
        }
    }
    await Promise.all(Array.from({length: Math.min(PARALLEL_WRITES, writes.length)}, worker))
    return {saved, failed, blocked, error}
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

export function bulkMessage(result: BulkResult): string {
    const parts = [`Zapisano ${result.saved}`]
    if (result.blocked) parts.push(`${result.blocked} do sprawdzenia w edytorze (brak analizy, kategorii lub pewnej nazwy)`)
    if (result.failed) parts.push(`${result.failed} nie udało się${result.error ? `: ${result.error}` : ''}`)
    return parts.join(' · ')
}
