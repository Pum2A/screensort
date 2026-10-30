import {test} from 'node:test'
import assert from 'node:assert/strict'
import {organizeStep, wantsAnalysis} from '../src/analysis/organize.js'
import type {OrganizeState} from '../src/analysis/organize.js'
import type {Batch} from '../src/analysis/batchApi.js'
import {foundNewImages} from '../src/analysis/autoAnalysis.js'

const idle: OrganizeState = {ready: false, busyElsewhere: false, fileCount: 12, desktop: true,
    engineStarting: false, engineError: null, engineRequested: false}

test('one click starts the engine once, waits for it, then analyses', () => {
    assert.equal(organizeStep(idle), 'start-engine')
    assert.equal(organizeStep({...idle, engineRequested: true, engineStarting: true}), 'wait')
    assert.equal(organizeStep({...idle, engineRequested: true, ready: true}), 'analyze')
})

test('a failed engine start ends the wait instead of retrying forever', () => {
    assert.equal(organizeStep({...idle, engineRequested: true, engineError: 'Pakiet AI nie jest zainstalowany.'}), 'give-up')
    // An error from an earlier attempt does not stop a fresh click from trying once more.
    assert.equal(organizeStep({...idle, engineError: 'stary błąd'}), 'start-engine')
})

test('a ready engine still waits for a scan, an edit or an empty folder', () => {
    assert.equal(organizeStep({...idle, ready: true, busyElsewhere: true}), 'wait')
    assert.equal(organizeStep({...idle, ready: true, fileCount: 0}), 'wait')
    assert.equal(organizeStep({...idle, desktop: false}), 'wait')
})

test('a finished analysis is repeated only for new images, a paused one is resumed', () => {
    const batch = {status: 'COMPLETED', total: 12} as Batch
    assert.equal(wantsAnalysis(null, 12), true)
    assert.equal(wantsAnalysis(batch, 12), false)
    assert.equal(wantsAnalysis(batch, 15), true)
    assert.equal(wantsAnalysis({...batch, status: 'PAUSED'}, 12), true)
    assert.equal(wantsAnalysis({...batch, status: 'RUNNING'}, 15), false)
})

test('only a folder read that found new or changed images starts an automatic analysis', () => {
    const summary = {newImages: 0, rescannedImages: 0, unchangedImages: 40, rejectedFiles: 2}
    assert.equal(foundNewImages(summary), false)
    assert.equal(foundNewImages({...summary, newImages: 3}), true)
    assert.equal(foundNewImages({...summary, rescannedImages: 1}), true)
})
