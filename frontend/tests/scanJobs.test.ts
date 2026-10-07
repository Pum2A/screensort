import {test} from 'node:test'
import assert from 'node:assert/strict'
import {fetchRejections, fetchScanJob, ScanApiError, startScan} from '../src/scanning/api.js'
import {observeScanJob, waitForPoll} from '../src/scanning/polling.js'
import type {ScanJob, ScanStatus} from '../src/scanning/types.js'

const id = '8e08e056-3fd4-4a6b-aebd-54df5072a641'
function snapshot(status: ScanStatus): ScanJob {
    return {id, folderPath: 'photos', status,
        summary: {newImages: 2, rescannedImages: 0, unchangedImages: 0, rejectedFiles: 1},
        errorMessage: status === 'FAILED' ? 'Nie udało się zapisać katalogu.' : null}
}
const signal = () => new AbortController().signal

test('start sends one POST with the folder and does not cache the ticket', async () => {
    const requests: [string, RequestInit | undefined][] = []
    const fetcher: typeof fetch = async (url, options) => {
        requests.push([String(url), options])
        return Response.json(snapshot('PREPARING'), {status: 202})
    }
    assert.equal((await startScan('photos', signal(), fetcher)).id, id)
    assert.equal(requests.length, 1)
    assert.equal(requests[0][0], '/api/scan/jobs')
    assert.equal(requests[0][1]?.method, 'POST')
    assert.equal(requests[0][1]?.cache, 'no-store')
    assert.deepEqual(JSON.parse(String(requests[0][1]?.body)), {folderPath: 'photos'})
})

test('status and rejection requests are GETs for the same encoded ID', async () => {
    const urls: string[] = []
    const fetcher: typeof fetch = async (url, options) => {
        urls.push(String(url))
        assert.equal(options?.method, undefined)
        assert.equal(options?.cache, 'no-store')
        return Response.json(snapshot('COMPLETED'))
    }
    await fetchScanJob('id/with space', signal(), fetcher)
    await fetchRejections(id, signal(), fetcher)
    assert.deepEqual(urls, ['/api/scan/jobs/id%2Fwith%20space', `/api/scan/jobs/${id}/rejections`])
})

for (const status of [400, 404, 409, 503, 500]) {
    test(`HTTP ${status} has a readable error without leaking the server response`, async () => {
        const fetcher: typeof fetch = async () => new Response('internal SQL password', {status})
        await assert.rejects(fetchScanJob(id, signal(), fetcher), (error: unknown) => {
            assert.ok(error instanceof ScanApiError)
            assert.equal(error.status, status)
            assert.ok(!error.message.includes('password'))
            return true
        })
    })
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
