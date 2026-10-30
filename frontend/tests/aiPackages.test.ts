import {test} from 'node:test'
import assert from 'node:assert/strict'
import {diskBytes, downloadBytes, hasAiPackage, installedSummary, packagePercent, packagePhase, packageSize} from '../src/tauri/packages/state.js'
import type {PackageStatus} from '../src/tauri/packages/state.js'

test('package progress is bounded and handles preparation without a byte count', () => {
    const progress = {phase: 'DOWNLOADING', busy: true, completedBytes: 25, totalBytes: 100, error: null}
    assert.equal(packagePercent(progress), 25)
    assert.equal(packagePercent({...progress, totalBytes: 0}), 0)
    assert.equal(packagePercent({...progress, completedBytes: 110}), 100)
    assert.equal(packagePercent({...progress, completedBytes: -5}), 0)
})

test('download and extracted sizes use binary units, not bits', () => {
    assert.equal(packageSize(64 * 1024 * 1024), '64 MiB')
    assert.equal(packagePhase('EXTRACTING'), 'Instalowanie składników analizy')
    assert.equal(packagePhase('UNKNOWN'), 'Silnik analizy')
})

test('AI starts only with an installed or bundled runtime, never a partial download', () => {
    const status: PackageStatus = {bundled: false, packages: [], progress: {phase: 'EXTRACTING', busy: true, completedBytes: 50, totalBytes: 100, error: null}}
    assert.equal(hasAiPackage(status), false)
    assert.equal(hasAiPackage({...status, bundled: true}), true)
    assert.equal(hasAiPackage({...status, packages: [{installed: true, package: {id: 'ai-cpu', name: 'AI', version: '1', archiveBytes: 3, unpackedBytes: 5, url: null, kind: 'python', files: []}}]}), true)
})

test('a Qwen package counts its model files and the summary names what is installed', () => {
    const qwen = {id: 'qwen-4b', name: 'Qwen 3.5 4B · rozpoznawanie zrzutów', version: '1', archiveBytes: 10, unpackedBytes: 20,
        url: null, kind: 'qwen' as const, files: [{bytes: 100}, {bytes: 5}]}
    const python = {id: 'ai-cpu', name: 'AI', version: '1', archiveBytes: 3, unpackedBytes: 5, url: null, kind: 'python' as const, files: []}
    assert.equal(downloadBytes(qwen), 115)
    assert.equal(diskBytes(qwen), 125)
    const status: PackageStatus = {bundled: false, packages: [{installed: true, package: qwen}, {installed: true, package: python}],
        progress: {phase: 'INSTALLED', busy: false, completedBytes: 0, totalBytes: 0, error: null}}
    assert.equal(installedSummary(status), 'Qwen 3.5 4B + RapidOCR + Florence-2 · lokalnie')
    assert.equal(installedSummary({...status, packages: [{installed: true, package: qwen}]}), 'Qwen 3.5 4B · lokalnie')
})
