import {afterEach, test} from 'node:test'
import assert from 'node:assert/strict'
import {findAnalysisJob, latestAnalysis, startAnalysis} from '../src/analysis/api.js'
import type {AnalysisJob, SavedAnalysis} from '../src/analysis/types.js'

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original })

// Complete objects shaped like the Java DTOs (AnalysisJob, SavedAnalysis): responses are validated now.
const saved: SavedAnalysis = {id: 1, fileId: 12, analyzedAt: '2026-10-20T10:00:00Z', matchesCatalog: true, result: {
    source_sha256: 'a'.repeat(64),
    ocr: {status: 'FAILED', result: null, error: {code: 'OCR_FAILED', message: 'OCR failed'}},
    visual: {status: 'COMPLETED', result: {description: 'A desk', model: 'florence', model_revision: 'r1',
        task: 'caption', device: 'cpu', duration_ms: 12}, error: null},
}}
const job: AnalysisJob = {id: 'ticket', fileId: 12, status: 'PREPARING', startedAt: '2026-10-20T10:00:00Z',
    analysis: null, errorMessage: null}

test('analysis sends an ID and requested steps, never a source path or image bytes', async () => {
    const signal = new AbortController().signal
    globalThis.fetch = async (input, init) => {
        assert.equal(input, '/api/analysis/jobs')
        assert.equal(init?.method, 'POST')
        assert.equal(init?.signal, signal)
        assert.deepEqual(JSON.parse(init?.body as string), {fileId: 12, ocr: true, visual: false})
        return Response.json(job, {status: 202})
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
    globalThis.fetch = async () => Response.json(saved)
    assert.deepEqual(await latestAnalysis(12, signal), saved)
    globalThis.fetch = async () => new Response('private server paths and stack trace', {status: 500})
    await assert.rejects(startAnalysis(12, true, true, signal), error =>
        error instanceof Error && !error.message.includes('private server'))
})

test('a step that breaks the backend contract is rejected instead of rendered', async () => {
    // A completed step must carry a result; this mirrors AnalysisResult.validateStep in Java.
    const broken = {...saved, result: {...saved.result, visual: {status: 'COMPLETED', result: null, error: null}}}
    globalThis.fetch = async () => Response.json(broken)
    await assert.rejects(latestAnalysis(12), {name: 'ApiError'})
})
