// The owner's 12×12 pixel icons from Figma (components px/*), plus the logo mark and the image placeholder.
const files = import.meta.glob<string>('./pixel/*.svg', {eager: true, query: '?url', import: 'default'})

export const pixelIcons = ['bell', 'camera', 'check', 'chevron-down', 'chevron-left', 'chevron-right', 'clock',
    'code', 'copy', 'cpu', 'download', 'drive', 'external', 'eye', 'file', 'folder', 'gamepad', 'grid', 'images',
    'inbox', 'layers', 'list', 'lock', 'maximize', 'message', 'more', 'pencil', 'plus', 'rotate', 'scan', 'search',
    'shield', 'sparkles', 'tag', 'type', 'undo', 'x', 'zoom-in', 'zoom-out'] as const
export type PixelIconName = typeof pixelIcons[number]

export function pixelIconUrl(name: PixelIconName | 'placeholder' | 'logo-mark'): string {
    const url = files[`./pixel/${name}.svg`]
    if (!url) throw new Error(`Missing pixel icon: ${name}`)
    return url
}
