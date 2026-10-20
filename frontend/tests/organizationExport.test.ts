import {test} from 'node:test'
import assert from 'node:assert/strict'
import {exportIssues, latestExport, readExport, startExport} from '../src/organization/export/api.js'

test('copying sends one batch and selected destination; the backend owns the approved file list', async () => {
    const original = globalThis.fetch
    globalThis.fetch = async (url, options) => {
        assert.equal(url, '/api/organization/batches/batch/exports')
        assert.equal(options?.method, 'POST')
        assert.deepEqual(JSON.parse(String(options?.body)), {destinationFolder: 'D:\\Test-output'})
        return Response.json({id: 'export', status: 'RUNNING', total: 120}, {status: 202})
    }
    try {assert.equal((await startExport('batch', 'D:\\Test-output')).total, 120)}
    finally {globalThis.fetch = original}
})

test('reopening progress reads the saved job and pages issues without starting another copy', async () => {
    const original = globalThis.fetch
    const urls: string[] = []
    globalThis.fetch = async (url, options) => {
        urls.push(String(url)); assert.equal(options?.method, undefined)
        if (String(url).endsWith('/latest')) return new Response(null, {status: 404})
        return Response.json(String(url).includes('/issues') ? {items: [], hasNext: false} : {id: 'saved-export'})
    }
    const signal = new AbortController().signal
    try {
        assert.equal(await latestExport('batch', signal), null)
        assert.equal((await readExport('saved-export', signal)).id, 'saved-export')
        assert.equal((await exportIssues('saved-export', 2, signal)).hasNext, false)
        assert.deepEqual(urls, ['/api/organization/batches/batch/exports/latest', '/api/organization/exports/saved-export',
            '/api/organization/exports/saved-export/issues?page=2'])
    } finally {globalThis.fetch = original}
})

test('destination errors stay readable and unknown server details are never displayed', async () => {
    const original = globalThis.fetch
    try {
        globalThis.fetch = async () => Response.json({code: 'DESTINATION_UNAVAILABLE', message: 'private server details'}, {status: 400})
        await assert.rejects(startExport('batch', 'missing'), /Nie można zapisywać/)
        globalThis.fetch = async () => Response.json({code: 'UNKNOWN', message: 'private server details'}, {status: 500})
        await assert.rejects(startExport('batch', 'missing'), /Nie udało się obsłużyć kopiowania/)
    } finally {globalThis.fetch = original}
})
