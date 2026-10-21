import test from 'node:test'
import assert from 'node:assert/strict'
import {installationBlocker, updatePercent} from '../src/desktop/updates/state.js'
import type {UpdateStatus} from '../src/desktop/updates/state.js'
const ready: UpdateStatus = {phase: 'DOWNLOADED', currentVersion: '0.2.2', version: '0.2.3', notes: null, downloadedBytes: 0, totalBytes: null, error: null}
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
