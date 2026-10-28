import {test} from 'node:test'
import assert from 'node:assert/strict'
import {fetchCatalog} from '../src/gallery/catalogApi.js'
import {ApiError} from '../src/http/http.js'

const file = {id: 7, allowedRootId: 1, sourceFolder: 'C:/images', relativePath: 'a.png', originalName: 'a.png',
    extension: 'png', sizeBytes: 120, lastModifiedAt: '2026-10-20T10:00:00Z', sha256: 'abc',
    firstDiscoveredAt: '2026-10-20T10:00:00Z', lastSeenAt: '2026-10-21T10:00:00Z'}

async function withFetch(stub: typeof fetch, run: () => Promise<void>) {
    const original = globalThis.fetch
    globalThis.fetch = stub
    try { await run() } finally { globalThis.fetch = original }
}

test('requests one catalog page for the folder', () => withFetch(async url => {
    assert.equal(url, '/api/catalog/page?folderPath=C%3A%2Fimages&pageNumber=2&pageSize=40')
    return Response.json({files: [file], pageNumber: 2, pageSize: 40, totalFiles: 81, hasNext: true})
}, async () => {
    const page = await fetchCatalog('C:/images', 2, 40)
    assert.equal(page.files[0].id, 7)
    assert.equal(page.hasNext, true)
}))

test('a response without the expected shape is rejected', () => withFetch(async () =>
    Response.json({files: [{...file, id: '7'}], pageNumber: 0, pageSize: 40, totalFiles: 1, hasNext: false}),
async () => {
    await assert.rejects(fetchCatalog('C:/images', 0, 40), (error: unknown) =>
        error instanceof ApiError && error.code === 'INVALID_RESPONSE')
}))

test('a server error never exposes its body', () => withFetch(async () => new Response('SQL C:/secret', {status: 500}),
async () => {
    await assert.rejects(fetchCatalog('C:/images', 0, 40), (error: unknown) =>
        error instanceof ApiError && error.status === 500 && !error.message.includes('secret'))
}))
