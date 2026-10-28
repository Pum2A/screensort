import {test} from 'node:test'
import assert from 'node:assert/strict'
import {isOutsideBox, treeConnections} from '../src/gallery/galleryLayout.js'

test('kliknięcie wewnątrz podglądu, także na krawędzi, nie jest kliknięciem tła', () => {
    const box = {left: 100, top: 80, width: 400, height: 300}
    for (const point of [{x: 300, y: 200}, {x: 100, y: 80}, {x: 500, y: 380}]) {
        assert.equal(isOutsideBox(point, box), false)
    }
})

test('kliknięcie tła jest wykrywane po każdej stronie podglądu', () => {
    const box = {left: 100, top: 80, width: 400, height: 300}
    for (const point of [{x: 99, y: 200}, {x: 501, y: 200}, {x: 300, y: 79}, {x: 300, y: 381}]) {
        assert.equal(isOutsideBox(point, box), true)
    }
})

test('bez gałęzi nie rysujemy połączeń ani linii do miniatury za modalem', () => {
    assert.deepEqual(treeConnections({left: 0, top: 20, width: 300, height: 300}, []), [])
})

test('desktop: połączenie prawej gałęzi biegnie nad węzłem, nie przez treść', () => {
    const paths = treeConnections({left: 0, top: 20, width: 300, height: 300},
        [{left: 332, top: 20, width: 240, height: 160}, {left: 600, top: 20, width: 240, height: 160}])
    assert.deepEqual(paths, [
        'M 300 42 H 312 V 8 H 320 V 42 H 332',
        'M 300 42 H 312 V 8 H 588 V 42 H 600',
    ])
})

test('telefon: pionowy pień leży w lewym odstępie między podglądem i gałęziami', () => {
    const paths = treeConnections({left: 16, top: 20, width: 272, height: 300},
        [{left: 16, top: 348, width: 272, height: 160}])
    assert.deepEqual(paths, ['M 16 42 H 4 V 370 H 16'])
})

test('rozwinięcie gałęzi zmienia położenie kolejnego połączenia', () => {
    const preview = {left: 16, top: 20, width: 272, height: 300}
    const before = treeConnections(preview, [{left: 16, top: 400, width: 272, height: 44}])
    const after = treeConnections(preview, [{left: 16, top: 520, width: 272, height: 160}])
    assert.equal(before[0], 'M 16 42 H 4 V 422 H 16')
    assert.equal(after[0], 'M 16 42 H 4 V 542 H 16')
})
