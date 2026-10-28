import {test} from 'node:test'
import assert from 'node:assert/strict'
import {organizationPlan, startBatch} from '../src/organization/api.js'
import {rejectionLabel} from '../src/gallery/reading/rejectionLabels.js'

test('analyze all sends the folder and options, not visible gallery IDs', async () => {
    const original = globalThis.fetch
    let body: unknown
    globalThis.fetch = async (url, init) => {
        assert.equal(url, '/api/organization/batches')
        body = JSON.parse(init?.body as string)
        return new Response(JSON.stringify({id: 'batch', folderPath: 'demo-images', status: 'RUNNING', ocr: true, visual: false, total: 120, completed: 0, failed: 0, processing: 0, createdAt: '2026-10-20T10:00:00Z'}), {status: 202})
    }
    try {
        const job = await startBatch('demo-images', true, false, new AbortController().signal)
        assert.equal(job.total, 120)
        assert.deepEqual(body, {folderPath: 'demo-images', ocr: true, visual: false})
    } finally { globalThis.fetch = original }
})

test('loads one page of the read-only organization plan for a batch', async () => {
    const original = globalThis.fetch
    globalThis.fetch = async url => {
        assert.equal(url, '/api/organization/batches/job/plan?page=3')
        return new Response(JSON.stringify({files: [], pageNumber: 3, pageSize: 50, totalFiles: 120, hasNext: false}))
    }
    try { assert.equal((await organizationPlan('job', 3, new AbortController().signal)).totalFiles, 120) }
    finally { globalThis.fetch = original }
})

test('rejection labels are user facing, including unknown future codes', () => {
    assert.equal(rejectionLabel('UNSUPPORTED_EXTENSION'), 'Nieobsługiwany format pliku')
    assert.equal(rejectionLabel('FUTURE_CODE'), 'Nie udało się sprawdzić tego wpisu')
})
