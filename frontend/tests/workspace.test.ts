import {test} from 'node:test'
import assert from 'node:assert/strict'
import {storedFolder} from '../src/app/state.js'
import {startupStep, elapsedLabel} from '../src/desktop/status.js'
import {effectiveCategory} from '../src/organization/review/filters.js'
import type {BatchItem} from '../src/organization/api.js'

const item: BatchItem = {fileId: 1, originalName: 'photo.png', status: 'COMPLETED', category: 'GAMES', proposedName: null, proposalUncertain: false,
    message: null, analysisId: 1, sourceSha256: 'a'.repeat(64), analysisMatchesCatalog: true,
    namingRevision: -1, approvedName: null, review: null}

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
