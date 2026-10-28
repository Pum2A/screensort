import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'

const css = readFileSync('src/app/index.css', 'utf8')
function token(name: string): string {
    const value = css.match(new RegExp(`--${name}:\\s*(#[a-f0-9]{3,6})`, 'i'))?.[1]
    assert.ok(value, `Missing theme token ${name}`)
    return value
}
function luminance(hex: string): number {
    const digits = hex.slice(1)
    const full = digits.length === 3 ? [...digits].map(value => value + value).join('') : digits
    const linear = [0, 2, 4].map(offset => {
        const value = parseInt(full.slice(offset, offset + 2), 16) / 255
        return value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4
    })
    return linear[0] * .2126 + linear[1] * .7152 + linear[2] * .0722
}
// The v1+ tokens in the @theme block; the older names (--text, --muted...) only point at them.
test('body text and small metadata meet AA contrast on both workspace surfaces', () => {
    for (const foreground of ['color-ink', 'color-muted']) for (const background of ['color-surface', 'color-canvas']) {
        const values = [luminance(token(foreground)), luminance(token(background))].sort((a, b) => b - a)
        assert.ok((values[0] + .05) / (values[1] + .05) >= 4.5, `${foreground} on ${background} must meet 4.5:1`)
    }
})

test('action and selected controls retain readable text on the restrained accent palette', () => {
    for (const [foreground, background] of [['color-ink', 'color-lime'], ['color-ink', 'color-fill']]) {
        const values = [luminance(token(foreground)), luminance(token(background))].sort((a, b) => b - a)
        assert.ok((values[0] + .05) / (values[1] + .05) >= 4.5, `${foreground} on ${background} must meet 4.5:1`)
    }
})
