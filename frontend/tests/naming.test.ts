import {afterEach, test} from 'node:test'
import assert from 'node:assert/strict'
import {approveNaming, readNaming} from '../src/naming/api.js'
import {fileNameProblem} from '../src/naming/fileNameRules.js'
import type {NamingReview} from '../src/naming/types.js'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })
const review: NamingReview = {fileId: 12, analysisId: 5, sourceSha256: 'a'.repeat(64),
    originalName: 'IMG_0026.png', proposedName: 'mug_desk.png', analysisMatchesCatalog: true,
    approvedName: null, approvedAt: null, approvalMatchesCatalog: false, revision: -1}

test('reads the saved analysis proposal by file ID, without starting analysis', async () => {
    const signal = new AbortController().signal
    globalThis.fetch = async (input, init) => {
        assert.equal(input, '/api/catalog/files/12/naming')
        assert.equal(init?.method ?? 'GET', 'GET')
        assert.equal(init?.signal, signal)
        return Response.json(review)
    }
    assert.deepEqual(await readNaming(12, signal), review)
})

test('approval sends edited name and source/review versions, never a source path', async () => {
    const signal = new AbortController().signal
    globalThis.fetch = async (input, init) => {
        assert.equal(input, '/api/catalog/files/12/naming')
        assert.equal(init?.method, 'PUT')
        assert.equal(init?.signal, signal)
        assert.deepEqual(JSON.parse(init?.body as string), {analysisId: 5, sourceSha256: 'a'.repeat(64),
            revision: -1, name: 'moj_kubek.png'})
        return Response.json({...review, approvedName: 'moj_kubek.png', revision: 0})
    }
    assert.equal((await approveNaming(review, 'moj_kubek.png', signal)).approvedName, 'moj_kubek.png')
})

test('conflicts and invalid names are clear, unknown server details remain private', async () => {
    const signal = new AbortController().signal
    globalThis.fetch = async () => Response.json({code: 'REVIEW_CHANGED'}, {status: 409})
    await assert.rejects(approveNaming(review, 'my.png', signal), /inną decyzję/)
    globalThis.fetch = async () => Response.json({code: 'INVALID_NAME'}, {status: 422})
    await assert.rejects(approveNaming(review, 'my.png', signal), /nieprawidłowa/)
    globalThis.fetch = async () => new Response('private SQL, paths and stack trace', {status: 500})
    await assert.rejects(readNaming(12, signal), error => error instanceof Error && !error.message.includes('private'))
})

test('allows Polish names, multiple dots and extension case without silently editing names', () => {
    for (const name of ['kubek_na_biurku.png', 'Moje zdjęcie.PNG', '2042.123.421.png']) {
        assert.equal(fileNameProblem(name, 'IMG_0026.png'), null)
    }
})

test('rejects paths, device names, controls, invalid extensions and whitespace', () => {
    for (const name of ['', ' ', '../photo.png', 'C:\\photo.png', 'a/b.png', 'a?b.png', 'a\nb.png',
        'a\u0000b.png', 'a\u202eb.png', 'CON.png', 'con .png', 'NUL.png', 'COM1.png', 'LPT9.png',
        'COM¹.png', 'CONIN$.png', 'CLOCK$.png', '.png', '..png', 'photo.jpg', 'photo.png ', 'photo.png.', ' photo.png']) {
        assert.notEqual(fileNameProblem(name, 'original.png'), null, name)
    }
})

test('limits both characters and UTF-8 bytes consistently with the backend', () => {
    assert.equal(fileNameProblem(`${'a'.repeat(251)}.png`, 'a.png'), null)
    assert.notEqual(fileNameProblem(`${'a'.repeat(252)}.png`, 'a.png'), null)
    assert.notEqual(fileNameProblem(`${'ą'.repeat(126)}.png`, 'a.png'), null)
})
