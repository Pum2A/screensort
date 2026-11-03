import {test} from 'node:test'
import assert from 'node:assert/strict'
import {organizeStep, wantsAnalysis} from '../src/analysis/organize.js'
import type {OrganizeState} from '../src/analysis/organize.js'
import type {Batch} from '../src/analysis/batchApi.js'
import {foundNewImages} from '../src/analysis/autoAnalysis.js'
import {imagesLabel} from '../src/app/imagesLabel.js'
import {etaLabel, feedEvents} from '../src/gallery/live.js'
import {copyGroups, copyRoot} from '../src/export/copyPlan.js'
import {decidedMessage} from '../src/gallery/useBulkReview.js'
import type {LibraryEntry} from '../src/gallery/model.js'

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

test('image counts on the first start use Polish plural forms', () => {
    assert.deepEqual([1, 2, 4, 5, 12, 14, 22, 25, 112, 312].map(imagesLabel),
        ['1 obraz', '2 obrazy', '4 obrazy', '5 obrazów', '12 obrazów', '14 obrazów', '22 obrazy', '25 obrazów', '112 obrazów', '312 obrazów'])
})

test('the live feed reports new proposals and failures, not decisions, and the ETA waits for a pace', () => {
    const file = (id: number) => ({id, originalName: `IMG_${id}.png`}) as LibraryEntry['file']
    const entry = (id: number, state: LibraryEntry['state'], proposedName: string | null = null) =>
        ({file: file(id), item: proposedName ? {proposedName} : null, state, category: null, copies: 1}) as LibraryEntry
    const before = new Map<number, LibraryEntry['state']>([[1, 'queued'], [2, 'queued'], [3, 'sure'], [4, 'fresh']])
    const events = feedEvents(before, [entry(1, 'sure', 'discord_czat.png'), entry(2, 'failed'), entry(3, 'approved'), entry(4, 'fresh')], 100)
    assert.deepEqual(events.map(event => [event.fileId, event.kind, event.detail]),
        [[1, 'named', 'discord_czat.png'], [2, 'failed', 'IMG_2.png']])
    assert.equal(etaLabel(1, 10_000, 20), null)
    assert.equal(etaLabel(10, 20_000, 30), 'ok. 1:00')
    assert.equal(etaLabel(4, 60_000, 400), 'ok. 1 h 40 min')
})

test('the organized copy holds only approved images, grouped by category in the app order', () => {
    const entry = (id: number, state: LibraryEntry['state'], category: LibraryEntry['category'], approvedName: string | null, sizeBytes = 10) =>
        ({file: {id, originalName: `IMG_${id}.png`, sizeBytes}, item: {approvedName}, state, category, copies: 1}) as LibraryEntry
    const groups = copyGroups([entry(1, 'approved', 'GAMES', 'roblox.png', 30), entry(2, 'approved', 'PROGRAMMING', 'kod.png'),
        entry(3, 'skipped', 'GAMES', null), entry(4, 'sure', 'GAMES', null), entry(5, 'approved', 'GAMES', 'minecraft.png', 5)])
    assert.deepEqual(groups.map(group => [group.category, group.files.map(file => file.name), group.bytes]),
        [['PROGRAMMING', ['kod.png'], 10], ['GAMES', ['roblox.png', 'minecraft.png'], 35]])
    assert.equal(copyRoot('D:\\Out\\'), 'D:\\Out\\ScreenSort')
    assert.equal(copyRoot('/home/me/out'), '/home/me/out/ScreenSort')
})

test('the undo toast names what was decided in Polish', () => {
    assert.equal(decidedMessage({decision: 'APPROVED'}, 1, null), 'Zatwierdzono 1 nazwę')
    assert.equal(decidedMessage({decision: 'APPROVED', category: 'GAMES'}, 3, 'Gry'), 'Zatwierdzono 3 nazwy · kategoria Gry')
    assert.equal(decidedMessage({decision: 'APPROVED'}, 12, null), 'Zatwierdzono 12 nazw')
    assert.equal(decidedMessage({decision: 'SKIPPED'}, 2, null), 'Pominięto 2 obrazy')
})
