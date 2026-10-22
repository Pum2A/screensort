import {test} from 'node:test'
import assert from 'node:assert/strict'
import {fetchRejections, fetchScanJob, startScan} from '../src/scanning/api.js'
import {ApiError} from '../src/shared/http.js'
import {observeScanJob, waitForPoll} from '../src/scanning/polling.js'
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

test('polling publishes stages sequentially and stops on COMPLETED', async () => {
    const updates: ScanStatus[] = []
    const queue: ScanStatus[] = ['SCANNING', 'SAVING', 'COMPLETED']
    let reading = false
    let waits = 0
    const finished = await observeScanJob(snapshot('PREPARING'), (job) => updates.push(job.status), signal(),
        async (requestedId) => {
            assert.equal(requestedId, id)
            assert.equal(reading, false)
            reading = true
            await Promise.resolve()
            reading = false
            return snapshot(queue.shift()!)
        }, async () => { assert.equal(reading, false); waits++ })
    assert.equal(finished.status, 'COMPLETED')
    assert.deepEqual(updates, ['PREPARING', 'SCANNING', 'SAVING', 'COMPLETED'])
    assert.equal(waits, 3)
    assert.equal(queue.length, 0)
})

test('FAILED is a terminal result, not a reason to restart the scan', async () => {
    let reads = 0
    const job = await observeScanJob(snapshot('SCANNING'), () => {}, signal(),
        async () => { reads++; return snapshot('FAILED') }, async () => {})
    assert.equal(job.errorMessage, 'Nie udało się zapisać katalogu.')
    assert.equal(reads, 1)
})

test('an already finished ticket needs no more GETs', async () => {
    await observeScanJob(snapshot('COMPLETED'), () => {}, signal(),
        async () => { throw new Error('Unnecessary GET') }, async () => { throw new Error('Unnecessary timer') })
})

test('a failed GET stops polling; resuming keeps the original ID', async () => {
    const initial = snapshot('SCANNING')
    await assert.rejects(observeScanJob(initial, () => {}, signal(),
        async () => { throw new TypeError('Network offline') }, async () => {}))
    const result = await observeScanJob(initial, () => {}, signal(),
        async (requestedId) => { assert.equal(requestedId, id); return snapshot('COMPLETED') }, async () => {})
    assert.equal(result.id, id)
})

test('abort ignores a late response rather than publishing stale state', async () => {
    const controller = new AbortController()
    const statuses: ScanStatus[] = []
    await assert.rejects(observeScanJob(snapshot('SCANNING'), (job) => statuses.push(job.status), controller.signal,
        async () => { controller.abort(); return snapshot('COMPLETED') }, async () => {}), {name: 'AbortError'})
    assert.deepEqual(statuses, ['SCANNING'])
})

test('a response belonging to another job is not published', async () => {
    const statuses: ScanStatus[] = []
    await assert.rejects(observeScanJob(snapshot('SCANNING'), (job) => statuses.push(job.status), signal(),
        async () => ({...snapshot('COMPLETED'), id: 'different-id'}), async () => {}))
    assert.deepEqual(statuses, ['SCANNING'])
})

test('abort cancels the waiting timer', async () => {
    const controller = new AbortController()
    const waiting = waitForPoll(controller.signal, 60000)
    controller.abort()
    await assert.rejects(waiting, {name: 'AbortError'})
})
