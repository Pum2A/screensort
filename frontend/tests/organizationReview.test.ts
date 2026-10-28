import {test} from 'node:test'
import assert from 'node:assert/strict'
import type {BatchItem} from '../src/analysis/batchApi.js'
import {draftChanged, draftFor, isCategory, reviewImageUrl, reviewLabel, reviewWrite, serverChanged} from '../src/review/state.js'
import {readReview, saveReview} from '../src/review/api.js'

const hash = 'a'.repeat(64)
const item: BatchItem = {fileId: 7, originalName: 'IMG_0026.png', status: 'COMPLETED', category: 'PHOTOGRAPHS',
    proposedName: 'mug_desk.png', proposalUncertain: false, message: null, analysisId: 8, sourceSha256: hash,
    analysisMatchesCatalog: true, namingRevision: -1, approvedName: null, review: null}
const batch = '11111111-2222-4333-8444-555555555555'

test('draft prefers a human name and valid decision category over the AI suggestion', () => {
    const approved = {...item, approvedName: 'moj_kubek.png', namingRevision: 2,
        review: {decision: 'APPROVED' as const, category: 'OTHER', revision: 3, matchesCatalog: true, reviewedAt: '2026-10-19T12:00:00Z'}}
    const draft = draftFor(approved)
    assert.equal(draft.name, 'moj_kubek.png')
    assert.equal(draft.category, 'OTHER')
    assert.equal(draftChanged(draft), false)
    assert.equal(draftChanged({...draft, name: 'inny_kubek.png'}), true)
    assert.equal(draftChanged({...draft, category: 'GAMES'}), true)
})

test('uncertain predictions require a choice and invalid category keys are never accepted', () => {
    assert.equal(draftFor({...item, category: 'REVIEW'}).category, '')
    assert.equal(isCategory('OTHER'), true)
    assert.equal(isCategory('REVIEW'), false)
    assert.equal(isCategory('__proto__'), false)
    assert.equal(isCategory(null), false)
})

test('a polling result cannot mutate the draft or silently advance its versions', () => {
    const draft = {...draftFor(item), name: 'wlasna_nazwa.png'}
    const updated = {...item, namingRevision: 1, approvedName: 'inna_osoba.png'}
    assert.equal(serverChanged(draft, updated), true)
    assert.equal(serverChanged(draft, {...item, status: 'COMPLETED'}), false)
    assert.equal(serverChanged(draft, {...item, sourceSha256: 'b'.repeat(64)}), true)
    assert.equal(serverChanged(draft, {...item, analysisId: 9}), true)
    assert.equal(serverChanged(draft, {...item, analysisMatchesCatalog: false}), true)
    assert.equal(draft.name, 'wlasna_nazwa.png')
    assert.equal(draft.base.namingRevision, -1)
    assert.equal(reviewWrite(draft, 'APPROVED').namingRevision, -1)
})

test('approval carries two concurrency versions, analysis hash and no paths or image bytes', () => {
    const request = reviewWrite({...draftFor(item), name: 'kubek.png', category: 'OTHER'}, 'APPROVED')
    assert.deepEqual(request, {analysisId: 8, sourceSha256: hash, namingRevision: -1, reviewRevision: -1,
        decision: 'APPROVED', name: 'kubek.png', category: 'OTHER'})
    assert.deepEqual(reviewWrite(draftFor(item), 'SKIPPED'), {...request, decision: 'SKIPPED', name: null, category: null})
    assert.throws(() => reviewWrite(draftFor({...item, analysisId: null}), 'APPROVED'), /wynik analizy/)
    assert.equal(reviewImageUrl(item, 'preview'), `/api/catalog/files/7/preview?v=${hash}`)
})

test('review statuses are human-readable and stale decisions are not shown as approved', () => {
    assert.equal(reviewLabel(item), 'Do sprawdzenia')
    assert.equal(reviewLabel({...item, status: 'FAILED'}), 'Błąd analizy')
    const reviewed: BatchItem = {...item, review: {decision: 'APPROVED', category: 'OTHER', revision: 0,
        reviewedAt: '2026-10-19T12:00:00Z', matchesCatalog: false}}
    assert.equal(reviewLabel(reviewed), 'Sprawdź ponownie')
    assert.equal(reviewLabel({...reviewed, review: {...reviewed.review!, matchesCatalog: true}}), 'Zatwierdzone')
})

test('PUT saves a decision and GET explicitly reloads the current database form', async () => {
    const original = globalThis.fetch
    const request = reviewWrite(draftFor(item), 'APPROVED')
    let calls = 0
    globalThis.fetch = async (url, init) => {
        assert.equal(url, `/api/organization/batches/${batch}/items/7/review`)
        if (calls++ === 0) {
            assert.equal(init?.method, 'PUT')
            assert.equal(new Headers(init?.headers).get('Content-Type'), 'application/json')
            assert.deepEqual(JSON.parse(init?.body as string), request)
        } else { assert.equal(init?.method, 'GET'); assert.equal(init?.body, undefined) }
        return Response.json({...item, namingRevision: 1})
    }
    try {
        const signal = new AbortController().signal
        assert.equal((await saveReview(batch, 7, request, signal)).namingRevision, 1)
        assert.equal((await readReview(batch, 7, signal)).namingRevision, 1)
        assert.equal(calls, 2)
    } finally { globalThis.fetch = original }
})

test('conflicts preserve a user-facing explanation and raw server errors never reach the UI', async () => {
    const original = globalThis.fetch
    try {
        globalThis.fetch = async () => Response.json({code: 'REVIEW_CHANGED', message: 'private SQL detail'}, {status: 409})
        await assert.rejects(saveReview(batch, 7, reviewWrite(draftFor(item), 'APPROVED'), new AbortController().signal), /edycja została zachowana/)
        globalThis.fetch = async () => Response.json({code: 'UNKNOWN', message: 'password=secret; select * from private'}, {status: 500})
        await assert.rejects(readReview(batch, 7, new AbortController().signal), error => {
            assert.ok(error instanceof Error)
            assert.doesNotMatch(error.message, /secret|select|private/)
            return true
        })
        globalThis.fetch = async () => Response.json({...item, fileId: 99})
        await assert.rejects(readReview(batch, 7, new AbortController().signal), /innego obrazu/)
    } finally { globalThis.fetch = original }
})
