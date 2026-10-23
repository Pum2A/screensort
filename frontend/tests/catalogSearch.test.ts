import {test} from 'node:test'
import assert from 'node:assert/strict'
import {fetchCatalog} from '../src/catalog/api.js'

const page = {files: [], pageNumber: 0, pageSize: 24, totalFiles: 0, hasNext: false}

async function requestedUrl(search?: string): Promise<URL> {
    let url = ''
    const original = globalThis.fetch
    globalThis.fetch = async input => {url = String(input); return new Response(JSON.stringify(page), {status: 200})}
    try { await fetchCatalog('C:/Shots', 0, 24, search) } finally { globalThis.fetch = original }
    return new URL(url, 'http://localhost')
}

test('a search is sent to the backend, trimmed, and covers the whole folder', async () => {
    const url = await requestedUrl('  Łódź ')
    assert.equal(url.pathname, '/api/catalog/page')
    assert.equal(url.searchParams.get('search'), 'Łódź')
    assert.equal(url.searchParams.get('pageNumber'), '0')
})

test('an empty search lists every file', async () => {
    assert.equal((await requestedUrl('   ')).searchParams.has('search'), false)
    assert.equal((await requestedUrl()).searchParams.has('search'), false)
})
