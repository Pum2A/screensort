import type {BatchItem} from '../analysis/batchApi.js'

export const categoryLabels = {PROGRAMMING: 'Programowanie', DOCUMENTS: 'Dokumenty', GAMES: 'Gry',
    PHOTOGRAPHS: 'Fotografie', OTHER: 'Pozostałe'} as const
export type ImageCategory = keyof typeof categoryLabels
export type ReviewDraft = {base: BatchItem; name: string; category: string}
export type ReviewWrite = {analysisId: number; sourceSha256: string; namingRevision: number;
    reviewRevision: number; decision: 'APPROVED' | 'SKIPPED'; name: string | null; category: string | null}

export function isCategory(value: string | null): value is ImageCategory {
    return value !== null && Object.hasOwn(categoryLabels, value)
}

export function draftFor(item: BatchItem): ReviewDraft {
    const previousCategory = item.review?.matchesCatalog ? item.review.category : null
    return {base: item, name: item.approvedName ?? item.proposedName ?? item.originalName,
        category: isCategory(previousCategory) ? previousCategory : isCategory(item.category) ? item.category : ''}
}

export function draftChanged(draft: ReviewDraft): boolean {
    const initial = draftFor(draft.base)
    return draft.name !== initial.name || draft.category !== initial.category
}

export function serverChanged(draft: ReviewDraft, item: BatchItem): boolean {
    return draft.base.analysisId !== item.analysisId || draft.base.sourceSha256 !== item.sourceSha256
        || draft.base.namingRevision !== item.namingRevision
        || (draft.base.review?.revision ?? -1) !== (item.review?.revision ?? -1)
        || draft.base.analysisMatchesCatalog !== item.analysisMatchesCatalog
}

export function reviewWrite(draft: ReviewDraft, decision: 'APPROVED' | 'SKIPPED'): ReviewWrite {
    if (draft.base.analysisId == null) throw new Error('Poczekaj na wynik analizy tego obrazu.')
    return {analysisId: draft.base.analysisId, sourceSha256: draft.base.sourceSha256,
        namingRevision: draft.base.namingRevision, reviewRevision: draft.base.review?.revision ?? -1, decision,
        name: decision === 'APPROVED' ? draft.name : null, category: decision === 'APPROVED' ? draft.category : null}
}

export function reviewLabel(item: BatchItem): string {
    if (item.review?.matchesCatalog) return item.review.decision === 'APPROVED' ? 'Zatwierdzone' : 'Pominięte'
    if (item.status === 'FAILED') return 'Błąd analizy'
    if (item.status === 'RUNNING') return 'Analizujemy…'
    if (item.status === 'PENDING') return 'W kolejce'
    return item.review ? 'Sprawdź ponownie' : 'Do sprawdzenia'
}

export function reviewImageUrl(item: BatchItem, kind: 'thumbnail' | 'preview'): string {
    return `/api/catalog/files/${item.fileId}/${kind}?${new URLSearchParams({v: item.sourceSha256})}`
}
