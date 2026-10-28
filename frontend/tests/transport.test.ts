import {test} from 'node:test'
import assert from 'node:assert/strict'
import {bridgeError} from '../src/tauri/transport.js'

test('a refusal from the desktop bridge becomes an Error the screens can show', () => {
    // Tauri hands over the Rust message as a bare string; 0.3.0 showed an endless skeleton because of it.
    const refused = bridgeError('Niedozwolone żądanie aplikacji.')
    assert.ok(refused instanceof Error)
    assert.equal(refused.message, 'Niedozwolone żądanie aplikacji.')
    const original = new Error('kept')
    assert.equal(bridgeError(original), original)
    assert.equal(bridgeError(undefined).message, 'Aplikacja nie mogła połączyć się z katalogiem.')
    assert.equal(bridgeError('  ').message, 'Aplikacja nie mogła połączyć się z katalogiem.')
})
