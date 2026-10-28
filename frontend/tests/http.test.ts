import {afterEach, test} from 'node:test'
import assert from 'node:assert/strict'
import {z} from 'zod'
import {ApiError, INVALID_RESPONSE_MESSAGE, requestJson, requestJsonOrNull} from '../src/http/http.js'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })
const schema = z.object({id: z.number(), name: z.string()})
const messages = {byCode: {TAKEN: 'Nazwa jest zajęta.'}, byStatus: {503: 'Usługa jest zajęta.'}, fallback: 'Coś poszło nie tak.'}

test('returns parsed data and sends JSON bodies with the given method and signal', async () => {
    const signal = new AbortController().signal
    globalThis.fetch = async (input, init) => {
        assert.equal(input, '/api/items')
        assert.equal(init?.method, 'POST')
        assert.equal(init?.signal, signal)
        assert.deepEqual(JSON.parse(init?.body as string), {name: 'a'})
        return Response.json({id: 1, name: 'a', extra: 'ignored'})
    }
    assert.deepEqual(await requestJson('/api/items', schema, messages, {method: 'POST', body: {name: 'a'}, signal}), {id: 1, name: 'a'})
})

test('maps errors by code first, then status, then fallback, never exposing the raw body', async () => {
    globalThis.fetch = async () => Response.json({code: 'TAKEN'}, {status: 409})
    await assert.rejects(requestJson('/x', schema, messages), (error: unknown) =>
        error instanceof ApiError && error.message === 'Nazwa jest zajęta.' && error.status === 409 && error.code === 'TAKEN')
    globalThis.fetch = async () => Response.json({code: 'UNKNOWN'}, {status: 503})
    await assert.rejects(requestJson('/x', schema, messages), {message: 'Usługa jest zajęta.'})
    globalThis.fetch = async () => new Response('private SQL, C:\\Users\\secret and stack trace', {status: 500})
    await assert.rejects(requestJson('/x', schema, messages), (error: unknown) =>
        error instanceof ApiError && error.message === 'Coś poszło nie tak.' && !error.message.includes('secret'))
})

test('rejects a successful response whose shape does not match the schema', async () => {
    globalThis.fetch = async () => Response.json({id: 'not-a-number'})
    await assert.rejects(requestJson('/x', schema, messages), (error: unknown) =>
        error instanceof ApiError && error.code === 'INVALID_RESPONSE' && error.message === INVALID_RESPONSE_MESSAGE)
})

test('treats 404 as "nothing yet" only where the caller asks for it', async () => {
    globalThis.fetch = async () => new Response(null, {status: 404})
    assert.equal(await requestJsonOrNull('/x', schema, messages), null)
    await assert.rejects(requestJson('/x', schema, messages), {message: 'Coś poszło nie tak.'})
})
