import {afterEach, test} from 'node:test'
import assert from 'node:assert/strict'
import {findAnalysisJob, latestAnalysis, startAnalysis} from '../src/analysis/api.js'

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original })

test('analysis sends an ID and requested steps, never a source path or image bytes', async () => {
    const signal = new AbortController().signal
    globalThis.fetch = async (input, init) => {
        assert.equal(input, '/api/analysis/jobs')
        assert.equal(init?.method, 'POST')
        assert.equal(init?.signal, signal)
        assert.deepEqual(JSON.parse(init?.body as string), {fileId: 12, ocr: true, visual: false})
        return Response.json({id: 'ticket', status: 'PREPARING'}, {status: 202})
    }
    assert.equal((await startAnalysis(12, true, false, signal)).id, 'ticket')
})

test('a missing job or saved analysis is empty, not a new analysis request', async () => {
    const requests: unknown[] = []
    globalThis.fetch = async (input, init) => {
        requests.push([input, init?.method ?? 'GET'])
        return new Response(null, {status: 404})
    }
    const signal = new AbortController().signal
    assert.equal(await findAnalysisJob('old-ticket', signal), null)
    assert.equal(await latestAnalysis(12, signal), null)
    assert.deepEqual(requests, [['/api/analysis/jobs/old-ticket', 'GET'], ['/api/catalog/files/12/analysis', 'GET']])
})

test('saved partial outcomes are preserved and server error details stay private', async () => {
    const signal = new AbortController().signal
    const saved = {id: 1, result: {ocr: {status: 'FAILED'}, visual: {status: 'COMPLETED', result: {description: 'A desk'}}}}
    globalThis.fetch = async () => Response.json(saved)
    assert.deepEqual(await latestAnalysis(12, signal), saved)
    globalThis.fetch = async () => new Response('private server paths and stack trace', {status: 500})
    await assert.rejects(startAnalysis(12, true, true, signal), error =>
        error instanceof Error && !error.message.includes('private server'))
})
