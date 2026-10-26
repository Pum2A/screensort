import type {CSSProperties} from 'react'
import {pixelIconUrl} from './pixelIcons.js'
import type {PixelIconName} from './pixelIcons.js'

/**
 * Each pixel SVG is drawn in one colour, so it is used as a mask and painted with currentColor:
 * the same icon works on ink, lime or white. Drawn small (10-14 px in the design); size is the edge in px.
 */
export function PixelIcon({name, size = 12, className = ''}: {name: PixelIconName, size?: number, className?: string}) {
    const style: CSSProperties = {width: size, height: size, maskImage: `url("${pixelIconUrl(name)}")`}
    return <span className={`pixel-icon ${className}`} style={style} aria-hidden="true"/>
}
