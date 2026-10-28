import {test} from 'node:test'
import assert from 'node:assert/strict'
import {imageFailure, imageFailureText, imageFailures, isPermanent} from '../src/gallery/imageFailure.js'
import {withRetry} from '../src/gallery/imageLoading.js'

test('reads the reason code sent by the desktop shell', () => {
    assert.equal(imageFailure('image:missing'), 'missing')
    assert.equal(imageFailure('image:too-large'), 'too-large')
    assert.equal(imageFailure('image:busy'), 'busy')
})

test('anything else is a plain unavailable preview', () => {
    assert.equal(imageFailure('Backend jest jeszcze uruchamiany.'), 'unavailable')
    assert.equal(imageFailure('image:something-new'), 'unavailable')
    assert.equal(imageFailure(new Error('image:missing')), 'unavailable')
    assert.equal(imageFailure(undefined), 'unavailable')
})

test('only problems with the file itself are permanent', () => {
    assert.deepEqual(imageFailures.filter(isPermanent), ['missing', 'blocked', 'too-large', 'unsupported'])
})

test('every reason has its own text', () => {
    for (const failure of imageFailures) assert.ok(imageFailureText[failure].label && imageFailureText[failure].detail)
})

test('a deleted file is not retried, a busy backend is', async () => {
    const signal = new AbortController().signal
    const options = {attempts: 3, pause: async () => {}, shouldRetry: (cause: unknown) => !isPermanent(imageFailure(cause))}
    let missingCalls = 0
    await assert.rejects(withRetry(async () => { missingCalls++; throw 'image:missing' }, signal, options))
    assert.equal(missingCalls, 1)

    let busyCalls = 0
    await assert.rejects(withRetry(async () => { busyCalls++; throw 'image:busy' }, signal, options))
    assert.equal(busyCalls, 3)
})
