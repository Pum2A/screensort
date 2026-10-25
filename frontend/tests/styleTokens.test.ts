import test from 'node:test'
import assert from 'node:assert/strict'
import {readdirSync, readFileSync} from 'node:fs'
import {join} from 'node:path'

// Colours live only in the @theme block of src/index.css. Everything else refers to those tokens,
// so a palette change is one edit and a stray colour cannot drift away from the design.
const files = readdirSync('src', {recursive: true, encoding: 'utf8'})
    .filter(file => /\.(css|tsx?)$/.test(file))
    .map(file => ({file, text: readFileSync(join('src', file), 'utf8')}))

function withoutTheme(file: string, text: string): string {
    const rules = text.replace(/\/\*[\s\S]*?\*\//g, '')
    return file === 'index.css' ? rules.replace(/@theme static \{[\s\S]*?\n\}/, '') : rules
}

test('no colour literal appears outside the theme tokens', () => {
    const literal = /#[0-9a-f]{3,8}\b|\brgba?\(|\bhsla?\(|(?<![\w-])(white|black)(?![\w-])/i
    for (const {file, text} of files.filter(({file}) => file.endsWith('.css'))) {
        const offending = withoutTheme(file, text).split('\n').filter(line => literal.test(line))
        assert.deepEqual(offending, [], `${file} must use var(--color-*) or var(--shadow-*) tokens`)
    }
})

test('components use token utilities instead of the default Tailwind palette', () => {
    const palette = /\b(bg|text|border|ring|outline|fill|stroke|from|to|via|divide|placeholder|accent|caret)-(slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|white|black)\b/
    for (const {file, text} of files.filter(({file}) => file.endsWith('.tsx'))) {
        assert.equal(palette.exec(text)?.[0], undefined, `${file} must use theme colours such as bg-accent or text-ink-muted`)
    }
})

test('the retired short variable names are gone', () => {
    const retired = /var\(--(muted|border|text|accent|soft|selected|surface|danger|success|warning|action|background|font-retro)\)/
    for (const {file, text} of files) {
        assert.equal(retired.exec(text)?.[0], undefined, `${file} still reads a pre-token variable`)
    }
})
