import {test} from 'node:test'
import assert from 'node:assert/strict'
import {watchNative} from '../src/desktop/nativeStatus.js'

test('outside the desktop shell a read fails, is reported, and stopping ends the watch', async () => {
    let errors = 0
    const values: unknown[] = []
    const stop = watchNative<string>('desktop_status', 'desktop-status', value => values.push(value), () => errors++)
    await new Promise(resolve => setTimeout(resolve, 20))
    stop()
    assert.equal(errors, 1)
    assert.deepEqual(values, [])
    await new Promise(resolve => setTimeout(resolve, 1_100))
    assert.equal(errors, 1)
})
