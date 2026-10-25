import {test} from 'node:test'
import assert from 'node:assert/strict'
import {createImageCache} from '../src/catalog/imageCache.js'
import {imageUrl} from '../src/catalog/api.js'
import type {CatalogFile} from '../src/catalog/types.js'

const blob = (bytes: number) => new Blob([new Uint8Array(bytes)])

test('returns a stored image and misses an unknown one', () => {
    const cache = createImageCache(10, 1000)
    const image = blob(10)
    cache.set('a', image)
    assert.equal(cache.get('a'), image)
    assert.equal(cache.get('b'), undefined)
})

test('over the entry limit drops the least recently read image', () => {
    const cache = createImageCache(2, 1000)
    cache.set('a', blob(1))
    cache.set('b', blob(1))
    cache.get('a')
    cache.set('c', blob(1))
    assert.notEqual(cache.get('a'), undefined)
    assert.equal(cache.get('b'), undefined)
    assert.notEqual(cache.get('c'), undefined)
})

test('over the byte limit drops the oldest images until it fits', () => {
    const cache = createImageCache(10, 100)
    cache.set('a', blob(40))
    cache.set('b', blob(40))
    cache.set('c', blob(40))
    assert.equal(cache.get('a'), undefined)
    assert.equal(cache.size, 2)
})

test('replacing an entry counts only the new size', () => {
    const cache = createImageCache(10, 100)
    cache.set('a', blob(60))
    cache.set('a', blob(30))
    cache.set('b', blob(60))
    assert.notEqual(cache.get('a'), undefined)
    assert.notEqual(cache.get('b'), undefined)
})

test('skips an image larger than the whole cache', () => {
    const cache = createImageCache(10, 100)
    cache.set('a', blob(101))
    assert.equal(cache.size, 0)
})

test('image address follows the file content, not the scan time', () => {
    const file: CatalogFile = {id: 7, allowedRootId: 1, sourceFolder: 'C:/shots', relativePath: 'a.png',
        originalName: 'a.png', extension: 'png', sizeBytes: 10, lastModifiedAt: '2026-10-20T10:00:00Z',
        sha256: 'ab'.repeat(32), firstDiscoveredAt: '2026-10-20T10:00:00Z', lastSeenAt: '2026-10-21T10:00:00Z'}
    const rescanned = {...file, lastSeenAt: '2026-10-23T10:00:00Z'}
    assert.equal(imageUrl(rescanned, 'thumbnail'), imageUrl(file, 'thumbnail'))
    assert.notEqual(imageUrl({...file, sha256: 'cd'.repeat(32)}, 'thumbnail'), imageUrl(file, 'thumbnail'))
})
