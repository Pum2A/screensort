import test from 'node:test'
import assert from 'node:assert/strict'
import {installationBlocker, updateLabel, updatePending, updatePercent} from '../src/desktop/updates/state.js'
import type {UpdateStatus} from '../src/desktop/updates/state.js'
const ready: UpdateStatus = {phase: 'DOWNLOADED', currentVersion: '0.2.2', version: '0.2.3', notes: null, downloadedBytes: 0, totalBytes: null, error: null}

test('startup status keeps polling until the native check actually starts and finishes', () => {
    assert.equal(updatePending('IDLE'), true)
    assert.equal(updatePending('CHECKING'), true)
    for (const phase of ['UP_TO_DATE', 'AVAILABLE', 'ERROR', 'DEVELOPMENT'] as const) assert.equal(updatePending(phase), false)
})
test('an offline or pending update check is never presented as an up-to-date installation', () => {
    assert.match(updateLabel({...ready, phase: 'CHECKING'}), /Sprawdzamy/)
    assert.match(updateLabel({...ready, phase: 'ERROR'}), /Sprawdź/)
    assert.match(updateLabel({...ready, phase: 'UP_TO_DATE'}), /aktualna/)
    assert.equal(updateLabel({...ready, phase: 'DEVELOPMENT'}), 'Tryb developerski')
})
test('update progress is indeterminate without a real total and bounded with one', () => {
    assert.equal(updatePercent(ready), undefined)
    assert.equal(updatePercent({...ready, totalBytes: 100, downloadedBytes: 40}), 40)
    assert.equal(updatePercent({...ready, totalBytes: 100, downloadedBytes: 120}), 100)
})
test('installation waits for a verified download and for work and drafts to finish', () => {
    assert.equal(installationBlocker([], ready), null)
    assert.match(installationBlocker([], {...ready, phase: 'AVAILABLE'})!, /pobierz/)
    assert.equal(installationBlocker(['Zapisz nazwę.', 'Zapisz nazwę.'], ready), 'Zapisz nazwę.')
})
