import {test} from 'node:test'
import assert from 'node:assert/strict'
import {createLimiter, wait, withRetry} from '../src/gallery/imageLoading.js'

function deferred() {
    let resolve!: () => void
    const promise = new Promise<void>(done => { resolve = done })
    return {promise, resolve}
}

const signal = () => new AbortController().signal

test('runs at most the limit at once and starts the rest in arrival order', async () => {
    const limiter = createLimiter(2)
    const gates = [deferred(), deferred(), deferred(), deferred()]
    const started: number[] = []
    let running = 0
    let peak = 0
    const runs = gates.map((gate, index) => limiter.run(async () => {
        started.push(index)
        running++
        peak = Math.max(peak, running)
        await gate.promise
        running--
        return index
    }, signal()))

    await Promise.resolve()
    assert.deepEqual(started, [0, 1])
    assert.equal(limiter.waiting, 2)
    for (const gate of gates) gate.resolve()
    assert.deepEqual(await Promise.all(runs), [0, 1, 2, 3])
    assert.deepEqual(started, [0, 1, 2, 3])
    assert.equal(peak, 2)
})

test('a cancelled waiting load leaves the queue without taking a slot', async () => {
    const limiter = createLimiter(1)
    const gate = deferred()
    const first = limiter.run(() => gate.promise, signal())
    const controller = new AbortController()
    let cancelledRan = false
    const cancelled = limiter.run(async () => { cancelledRan = true }, controller.signal)
    const third = limiter.run(async () => 'third', signal())

    controller.abort()
    await assert.rejects(cancelled, {name: 'AbortError'})
    assert.equal(limiter.waiting, 1)
    gate.resolve()
    await first
    assert.equal(await third, 'third')
    assert.equal(cancelledRan, false)
})

test('a failing task still frees its slot for the next one', async () => {
    const limiter = createLimiter(1)
    await assert.rejects(limiter.run(async () => { throw new Error('decode failed') }, signal()))
    assert.equal(await limiter.run(async () => 'next', signal()), 'next')
})

test('retries with growing pauses and returns the first success', async () => {
    const pauses: number[] = []
    let calls = 0
    const result = await withRetry(async () => {
        calls++
        if (calls < 3) throw new Error('busy')
        return 'image'
    }, signal(), {attempts: 3, delayMs: 1000, pause: async ms => { pauses.push(ms) }})
    assert.equal(result, 'image')
    assert.deepEqual(pauses, [1000, 2000])
})

test('gives up after the last attempt with the last error', async () => {
    let calls = 0
    await assert.rejects(withRetry(async () => { calls++; throw new Error(`failure ${calls}`) },
        signal(), {attempts: 3, pause: async () => {}}), {message: 'failure 3'})
    assert.equal(calls, 3)
})

test('cancelling stops further attempts', async () => {
    const controller = new AbortController()
    let calls = 0
    await assert.rejects(withRetry(async () => { calls++; controller.abort(); throw new Error('busy') },
        controller.signal, {attempts: 3, pause: async () => {}}), {name: 'AbortError'})
    assert.equal(calls, 1)
})

test('a pause ends early when cancelled', async () => {
    const controller = new AbortController()
    const pausing = wait(60_000, controller.signal)
    controller.abort()
    await assert.rejects(pausing, {name: 'AbortError'})
})
