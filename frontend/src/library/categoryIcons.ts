import type {ImageCategory} from '../organization/review/state.js'
import type {PixelIconName} from '../ui/pixelIcons.js'

export const categoryIcons: Record<ImageCategory, PixelIconName> = {
    GAMES: 'gamepad', PROGRAMMING: 'code', DOCUMENTS: 'file', PHOTOGRAPHS: 'camera', OTHER: 'layers',
}
