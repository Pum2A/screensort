import {test} from 'node:test'
import assert from 'node:assert/strict'
import {initialStartup, MAX_STATUS_FAILURES, nextStartup, startupView} from '../src/tauri/status.js'
import type {DesktopStatus, StartupState} from '../src/tauri/status.js'

const starting: DesktopStatus = {phase: 'Przygotowujemy bazę', backendReady: false, analysisReady: false,
    analysisStarting: false, error: null}
const ready: DesktopStatus = {...starting, phase: 'Katalog gotowy', backendReady: true}

const failTimes = (state: StartupState, times: number) =>
    Array.from({length: times}).reduce<StartupState>(current => nextStartup(current, {kind: 'failed'}), state)

test('one missed status during startup is not an error yet', () => {
    const state = nextStartup(nextStartup(initialStartup, {kind: 'status', status: starting}), {kind: 'failed'})
    const view = startupView(state)
    assert.equal(view.screen, 'startup')
    assert.equal(view.status?.error, null)
})

test('repeated missed statuses during startup show the error screen', () => {
    const view = startupView(failTimes(initialStartup, MAX_STATUS_FAILURES))
    assert.equal(view.screen, 'startup')
    assert.match(view.status?.error ?? '', /Nie udało się odczytać stanu/)
})

test('after the first ready backend the app stays and a lost connection is a banner', () => {
    const readyOnce = nextStartup(initialStartup, {kind: 'status', status: ready})
    assert.deepEqual(startupView(readyOnce), {screen: 'app', status: ready, connectionLost: false})

    const lost = failTimes(readyOnce, MAX_STATUS_FAILURES)
    assert.equal(startupView(lost).screen, 'app')
    assert.equal(startupView(lost).connectionLost, true)

    const crashed = nextStartup(readyOnce, {kind: 'status', status: {...ready, backendReady: false}})
    assert.equal(startupView(crashed).screen, 'app')
    assert.equal(startupView(crashed).connectionLost, true)
})

test('a good answer clears the failure count', () => {
    const recovered = nextStartup(failTimes(initialStartup, MAX_STATUS_FAILURES - 1), {kind: 'status', status: starting})
    assert.equal(recovered.failures, 0)
    assert.equal(startupView(failTimes(recovered, MAX_STATUS_FAILURES - 1)).status?.error, null)
})
