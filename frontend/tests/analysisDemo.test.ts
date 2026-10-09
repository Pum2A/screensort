import assert from 'node:assert/strict'
import {test} from 'node:test'
import {analysisExamples} from '../src/analysis/demo/examples.js'
import {demoNameError} from '../src/analysis/demo/reviewName.js'

test('each fictional example has its own ID and a valid proposed name', () => {
    assert.equal(new Set(analysisExamples.map(example => example.id)).size, analysisExamples.length)
    for (const example of analysisExamples) {
        assert.equal(demoNameError(example.proposedName, example.originalName), null)
        assert.equal('allowedRootId' in example, false)
        assert.equal('sourceFolder' in example, false)
    }
})

test('editing a copied proposal does not change the example data', () => {
    const example = analysisExamples[0]
    const original = example.proposedName
    const review = {name: example.proposedName, approved: false}
    review.name = 'moja_propozycja.png'
    review.approved = true
    assert.equal(example.proposedName, original)
})

test('proposal accepts multiple dots and a matching extension in either case', () => {
    assert.equal(demoNameError('2042.123.421.PNG', 'sample.png'), null)
})

test('proposal rejects an empty or whitespace-only name', () => {
    assert.notEqual(demoNameError('', 'sample.png'), null)
    assert.notEqual(demoNameError('   ', 'sample.png'), null)
})

test('proposal rejects paths and control characters', () => {
    for (const name of ['../sample.png', 'C:\\sample.png', 'a?b.png', 'a\nb.png', 'a\u0000b.png']) {
        assert.notEqual(demoNameError(name, 'sample.png'), null)
    }
})

test('proposal rejects common reserved Windows device names', () => {
    for (const name of ['CON.png', 'nul.png', 'COM1.png', 'LPT9.png']) {
        assert.notEqual(demoNameError(name, 'sample.png'), null)
    }
})

test('proposal keeps the original file extension and needs a basename', () => {
    assert.notEqual(demoNameError('sample.jpg', 'sample.png'), null)
    assert.notEqual(demoNameError('.png', 'sample.png'), null)
})

test('proposal rejects trailing spaces, dots and excessive length', () => {
    for (const name of ['sample.png ', 'sample.png.', `${'a'.repeat(252)}.png`]) {
        assert.notEqual(demoNameError(name, 'sample.png'), null)
    }
    assert.equal(demoNameError(`${'a'.repeat(251)}.png`, 'sample.png'), null)
})
