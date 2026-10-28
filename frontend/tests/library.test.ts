import {test} from 'node:test'
import assert from 'node:assert/strict'
import {bulkPlan, countEntries, duplicateGroups, entryState, filterEntries, inReviewTab, lastScan, libraryEntries, selectRange,
    sortEntries, todayCount} from '../src/gallery/model.js'
import type {CatalogFile} from '../src/gallery/catalogTypes.js'
import type {BatchItem} from '../src/organization/types.js'

function file(id: number, name: string, sha: string, modified: string, size = 100): CatalogFile {
    return {id, allowedRootId: 1, sourceFolder: 'C:\\root', relativePath: name, originalName: name, extension: 'png',
        sizeBytes: size, lastModifiedAt: modified, sha256: sha.repeat(64), firstDiscoveredAt: modified, lastSeenAt: modified}
}
function item(fileId: number, patch: Partial<BatchItem> = {}): BatchItem {
    return {fileId, originalName: `f${fileId}.png`, status: 'COMPLETED', category: 'GAMES', proposedName: 'lobby.png',
        proposalUncertain: false, message: null, analysisId: 10 + fileId, sourceSha256: 'a'.repeat(64), analysisMatchesCatalog: true,
        namingRevision: -1, approvedName: null, review: null, ...patch}
}
const decision = (value: 'APPROVED' | 'SKIPPED', matchesCatalog = true) =>
    ({decision: value, category: value === 'APPROVED' ? 'DOCUMENTS' : null, reviewedAt: '2026-10-25T10:00:00Z', revision: 2, matchesCatalog})

test('entry state separates decided, waiting, running and never analysed files', () => {
    assert.equal(entryState(null), 'fresh')
    assert.equal(entryState(item(1)), 'sure')
    assert.equal(entryState(item(1, {proposalUncertain: true})), 'check')
    assert.equal(entryState(item(1, {category: 'REVIEW'})), 'check')
    assert.equal(entryState(item(1, {proposedName: null})), 'check')
    assert.equal(entryState(item(1, {status: 'RUNNING'})), 'queued')
    assert.equal(entryState(item(1, {status: 'FAILED'})), 'failed')
    assert.equal(entryState(item(1, {analysisMatchesCatalog: false})), 'fresh')
    assert.equal(entryState(item(1, {review: decision('APPROVED')})), 'approved')
    assert.equal(entryState(item(1, {review: decision('SKIPPED')})), 'skipped')
    // A decision about an older version of the file is not a decision about this one.
    assert.equal(entryState(item(1, {review: decision('APPROVED', false)})), 'sure')
})

test('entries take the approved category over the suggestion and count per category', () => {
    const library = {batchId: 'b', files: [file(1, 'a.png', 'a', '2026-10-25T10:00:00Z'), file(2, 'b.png', 'b', '2026-10-25T11:00:00Z'),
        file(3, 'c.png', 'c', '2026-10-25T12:00:00Z')], items: [item(1, {review: decision('APPROVED')}), item(2)]}
    const entries = libraryEntries(library)
    assert.deepEqual(entries.map(entry => [entry.state, entry.category]), [['approved', 'DOCUMENTS'], ['sure', 'GAMES'], ['fresh', null]])
    const counts = countEntries(entries)
    assert.equal(counts.pending, 1)
    assert.deepEqual(counts.byCategory.GAMES, {total: 1, pending: 1})
    assert.deepEqual(counts.byCategory.DOCUMENTS, {total: 1, pending: 0})
    assert.deepEqual(filterEntries(entries, 'pending', '').map(entry => entry.file.id), [2])
    assert.deepEqual(filterEntries(entries, 'all', ' B.P ').map(entry => entry.file.id), [2])
    assert.deepEqual(sortEntries(entries, 'newest').map(entry => entry.file.id), [3, 2, 1])
})

test('duplicates are byte-identical files, oldest first, biggest waste first', () => {
    const files = [file(1, 'kopia.png', 'a', '2026-10-25T12:00:00Z', 10), file(2, 'oryginal.png', 'a', '2026-10-24T12:00:00Z', 10),
        file(3, 'sam.png', 'b', '2026-10-25T12:00:00Z'), file(4, 'big.png', 'c', '2026-10-25T12:00:00Z', 500),
        file(5, 'big2.png', 'c', '2026-10-25T12:00:00.5Z', 500)]
    const groups = duplicateGroups(files)
    assert.deepEqual(groups.map(group => group.files.map(item => item.id)), [[4, 5], [2, 1]])
    assert.deepEqual(groups.map(group => group.extraBytes), [500, 10])
    assert.equal(libraryEntries({batchId: null, files, items: []})[0].copies, 2)
})

test('shift range selects everything between the anchor and the target in screen order', () => {
    const order = [5, 3, 9, 1]
    assert.deepEqual([...selectRange(order, 3, 1, new Set([7]))].sort(), [1, 3, 7, 9])
    assert.deepEqual([...selectRange(order, 1, 5, new Set())].sort(), [1, 3, 5, 9])
    assert.deepEqual([...selectRange(order, null, 9, new Set())], [9])
})

test('bulk plan writes the same review as the editor and never guesses a missing category', () => {
    const library = {batchId: 'b', files: [1, 2, 3, 4].map(id => file(id, `${id}.png`, String(id), '2026-10-25T10:00:00Z')),
        items: [item(1), item(2, {category: 'REVIEW'}), item(3, {status: 'RUNNING'}), item(4, {proposalUncertain: true})]}
    const entries = libraryEntries(library)
    const approve = bulkPlan(entries, {decision: 'APPROVED'})
    assert.equal(approve.blocked, 3)
    // Item 4 has a category but a doubtful name: the category menu must not approve it either.
    assert.deepEqual(approve.writes, [{fileId: 1, write: {analysisId: 11, sourceSha256: 'a'.repeat(64), namingRevision: -1,
        reviewRevision: -1, decision: 'APPROVED', name: 'lobby.png', category: 'GAMES'}}])
    const recategorize = bulkPlan(entries, {decision: 'APPROVED', category: 'OTHER'})
    assert.deepEqual(recategorize.writes.map(write => [write.fileId, write.write.category]), [[1, 'OTHER'], [2, 'OTHER']])
    const skip = bulkPlan(entries, {decision: 'SKIPPED'})
    assert.equal(recategorize.blocked, 2)
    assert.deepEqual(skip.writes.map(write => [write.fileId, write.write.name, write.write.category]), [[1, null, null], [2, null, null], [4, null, null]])
})

test('scan summary counts files found today and the latest scan time', () => {
    const files = [file(1, 'a.png', 'a', '2026-10-25T08:00:00Z'), file(2, 'b.png', 'b', '2026-10-20T08:00:00Z')]
    assert.equal(todayCount(files, new Date('2026-10-25T12:00:00Z')), 1)
    assert.equal(lastScan(files), '2026-10-25T08:00:00Z')
    assert.equal(lastScan([]), null)
})

test('card picks set the category per file, a bar category still wins, review tabs split the states', () => {
    const library = {batchId: 'b', files: [1, 2].map(id => file(id, `${id}.png`, String(id), '2026-10-25T10:00:00Z')),
        items: [item(1), item(2, {category: 'REVIEW'})]}
    const entries = libraryEntries(library)
    const picks = new Map([[2, 'DOCUMENTS' as const]])
    assert.deepEqual(bulkPlan(entries, {decision: 'APPROVED', overrides: picks}).writes.map(write => [write.fileId, write.write.category]),
        [[1, 'GAMES'], [2, 'DOCUMENTS']])
    assert.deepEqual(bulkPlan(entries, {decision: 'APPROVED', category: 'OTHER', overrides: picks}).writes.map(write => write.write.category),
        ['OTHER', 'OTHER'])
    assert.deepEqual(entries.map(entry => [inReviewTab(entry, 'pending'), inReviewTab(entry, 'sure'), inReviewTab(entry, 'check')]),
        [[true, true, false], [true, false, true]])
})
