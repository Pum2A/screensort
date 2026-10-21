import {test} from 'node:test'
import assert from 'node:assert/strict'
import {filterCatalogPage, groupCatalogPage, storedFolder} from '../src/workspace/state.js'
import {startupStep, elapsedLabel} from '../src/desktop/status.js'
import {effectiveCategory, filterReviewPage} from '../src/organization/review/filters.js'
import type {CatalogFile} from '../src/catalog/types.js'
import type {BatchItem} from '../src/organization/api.js'

const files = [{id: 1, originalName: 'Łódź.PNG', extension: 'png'}, {id: 2, originalName: 'foto.jpeg', extension: 'jpeg'},
    {id: 3, originalName: 'inne.jpg', extension: 'jpg'}] as CatalogFile[]
const item: BatchItem = {fileId: 1, originalName: 'photo.png', status: 'COMPLETED', category: 'GAMES', proposedName: null,
    message: null, analysisId: 1, sourceSha256: 'a'.repeat(64), analysisMatchesCatalog: true,
    namingRevision: -1, approvedName: null, review: null}

test('catalog search is local to the provided page, case insensitive and does not mutate it', () => {
    assert.deepEqual(filterCatalogPage(files, ' łÓdŹ '), [files[0]])
    assert.deepEqual(filterCatalogPage(files, 'not-here'), [])
    assert.equal(filterCatalogPage(files, '  '), files)
    assert.equal(files.length, 3)
})
test('format branches group jpg/jpeg together without inventing categories', () => {
    const grouped = groupCatalogPage(files)
    assert.equal(grouped[0].format, 'PNG')
    assert.equal(grouped[1].format, 'JPG / JPEG')
    assert.deepEqual(grouped[1].files, [files[1], files[2]])
})
test('blocked or unavailable local storage does not prevent the first render', () => {
    assert.equal(storedFolder(true), '')
    assert.equal(storedFolder(false), 'demo-images')
})
test('startup does not invent model steps or percentages from unknown phase text', () => {
    const status = {phase: 'Przygotowanie aplikacji', backendReady: false, analysisReady: false, analysisStarting: false, error: null}
    assert.equal(startupStep(null), 0)
    assert.equal(startupStep(status), 0)
    assert.equal(startupStep({...status, phase: 'Uruchamiamy backend i sprawdzamy migracje…'}), 1)
    assert.equal(startupStep({...status, backendReady: true}), 2)
    assert.equal(elapsedLabel(65), '1 min 5 s')
    assert.equal(elapsedLabel(-1), '0 s')
})
test('review categories use current human decisions and never trust stale decisions', () => {
    const reviewed = {...item, review: {decision: 'APPROVED' as const, category: 'DOCUMENTS', revision: 1, reviewedAt: '', matchesCatalog: true}}
    assert.equal(effectiveCategory(reviewed), 'DOCUMENTS')
    assert.equal(effectiveCategory({...reviewed, review: {...reviewed.review, matchesCatalog: false}}), 'GAMES')
    assert.equal(effectiveCategory({...item, category: 'UNKNOWN'}), 'REVIEW')
})
test('review filters keep approved, skipped, pending and failed states distinct', () => {
    const approved = {...item, fileId: 2, review: {decision: 'APPROVED' as const, category: 'GAMES', revision: 0, reviewedAt: '', matchesCatalog: true}}
    const skipped = {...item, fileId: 3, review: {...approved.review, decision: 'SKIPPED' as const}}
    const failed = {...item, fileId: 4, status: 'FAILED' as const}
    const pending = {...item, fileId: 5, status: 'PENDING' as const}
    const page = [item, approved, skipped, failed, pending]
    assert.deepEqual(filterReviewPage(page, 'APPROVED'), [approved])
    assert.deepEqual(filterReviewPage(page, 'SKIPPED'), [skipped])
    assert.deepEqual(filterReviewPage(page, 'UNREVIEWED'), [item])
    assert.deepEqual(filterReviewPage(page, 'FAILED'), [failed])
    assert.equal(filterReviewPage(page, 'category:GAMES').length, 5)
    assert.deepEqual(filterReviewPage(page, 'unknown'), [])
})
