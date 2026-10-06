import {test} from 'node:test'
import assert from 'node:assert/strict'
import {selectedRowEnd, treeConnections} from '../src/catalog/galleryLayout.js'

test('brak wyboru nie wstawia drzewka', () => {
    assert.equal(selectedRowEnd(-1, 4, 50), -1)
    assert.equal(selectedRowEnd(0, 4, 0), -1)
    assert.equal(selectedRowEnd(50, 4, 50), -1)
    assert.equal(selectedRowEnd(0, 0, 50), -1)
})

test('wybór w tym samym rzędzie rozwija drzewko po jego ostatnim elemencie', () => {
    for (const index of [0, 1, 2, 3]) assert.equal(selectedRowEnd(index, 4, 50), 3)
    for (const index of [4, 5, 6, 7]) assert.equal(selectedRowEnd(index, 4, 50), 7)
})

test('ostatni niepełny rząd nie gubi szczegółów', () => {
    assert.equal(selectedRowEnd(49, 4, 50), 49)
    assert.equal(selectedRowEnd(0, 8, 2), 1)
})

test('zmiana szerokości okna przenosi drzewko pod nowy koniec rzędu', () => {
    assert.equal(selectedRowEnd(4, 4, 50), 7)
    assert.equal(selectedRowEnd(4, 2, 50), 5)
    assert.equal(selectedRowEnd(4, 1, 50), 4)
})

test('desktop: połączenie prawej gałęzi biegnie nad węzłem, nie przez treść', () => {
    const paths = treeConnections({left: 0, top: 32, width: 300, height: 300},
        [{left: 332, top: 32, width: 240, height: 160}, {left: 600, top: 32, width: 240, height: 160}], 80, -64)
    assert.deepEqual(paths, [
        'M 80 -64 V 12 H 150 V 32',
        'M 300 54 H 312 V 20 H 320 V 54 H 332',
        'M 300 54 H 312 V 20 H 588 V 54 H 600',
    ])
})

test('telefon: pionowy pień leży w lewym odstępie między podglądem i gałęziami', () => {
    const paths = treeConnections({left: 16, top: 32, width: 272, height: 300},
        [{left: 16, top: 360, width: 272, height: 160}], 72, -60)
    assert.equal(paths[1], 'M 16 54 H 4 V 382 H 16')
})
