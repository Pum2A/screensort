import {test} from 'node:test'
import assert from 'node:assert/strict'
import {fetchRejections, fetchScanJob, startScan} from '../src/scanning/api.js'
import {ApiError} from '../src/shared/http.js'
import {SCAN_POLL_MS, scanPollInterval} from '../src/scanning/types.js'
import type {ScanJob, ScanStatus} from '../src/scanning/types.js'

const id = '8e08e056-3fd4-4a6b-aebd-54df5072a641'
function snapshot(status: ScanStatus): ScanJob {
    return {id, folderPath: 'photos', status,
        summary: {newImages: 2, rescannedImages: 0, unchangedImages: 0, rejectedFiles: 1},
        errorMessage: status === 'FAILED' ? 'Nie udało się zapisać katalogu.' : null}
}
const signal = () => new AbortController().signal

async function withFetch(stub: typeof fetch, run: () => Promise<void>) {
    const original = globalThis.fetch
    globalThis.fetch = stub
    try { await run() } finally { globalThis.fetch = original }
}

test('start sends one POST with the folder', async () => {
    const requests: [string, RequestInit | undefined][] = []
    await withFetch(async (url, options) => {
        requests.push([String(url), options])
        return Response.json(snapshot('PREPARING'), {status: 202})
    }, async () => { assert.equal((await startScan('photos', signal())).id, id) })
    assert.equal(requests.length, 1)
    assert.equal(requests[0][0], '/api/scan/jobs')
    assert.equal(requests[0][1]?.method, 'POST')
    assert.deepEqual(JSON.parse(String(requests[0][1]?.body)), {folderPath: 'photos'})
})

test('status and rejection requests are GETs for the same encoded ID', async () => {
    const urls: string[] = []
    await withFetch(async (url, options) => {
        urls.push(String(url))
        assert.equal(options?.method, 'GET')
        return String(url).endsWith('/rejections')
            ? Response.json({files: [{path: 'a.txt', originalName: 'a.txt', reason: 'UNSUPPORTED_EXTENSION'}], totalRejected: 1, truncated: false})
            : Response.json(snapshot('COMPLETED'))
    }, async () => {
        await fetchScanJob('id/with space', signal())
        await fetchRejections(id, signal())
    })
    assert.deepEqual(urls, ['/api/scan/jobs/id%2Fwith%20space', `/api/scan/jobs/${id}/rejections`])
})

test('a snapshot with an unknown status is rejected rather than shown', () =>
    withFetch(async () => Response.json({...snapshot('SCANNING'), status: 'PAUSED'}), async () => {
        await assert.rejects(fetchScanJob(id, signal()), (error: unknown) =>
            error instanceof ApiError && error.code === 'INVALID_RESPONSE')
    }))

for (const status of [400, 404, 409, 503, 500]) {
    test(`HTTP ${status} has a readable error without leaking the server response`, () =>
        withFetch(async () => new Response('internal SQL password', {status}), async () => {
            await assert.rejects(fetchScanJob(id, signal()), (error: unknown) => {
                assert.ok(error instanceof ApiError)
                assert.equal(error.status, status)
                assert.ok(!error.message.includes('password'))
                return true
            })
        }))
}

test('polling continues only while the scan is unfinished', () => {
    for (const status of ['PREPARING', 'SCANNING', 'SAVING'] as const) assert.equal(scanPollInterval(snapshot(status)), SCAN_POLL_MS)
    // FAILED is a terminal result, not a reason to restart the scan.
    for (const status of ['COMPLETED', 'FAILED'] as const) assert.equal(scanPollInterval(snapshot(status)), false)
})
