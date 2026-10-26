import {pixelIconUrl} from './pixelIcons.js'

/** Logo "I · retro pixel cursor": a 1-bit arrow cursor dragging a lime tile, with the Geist wordmark. */
export function Logo({markSize = 25.6}: {markSize?: number}) {
    return <span className="logo-lockup">
        <img src={pixelIconUrl('logo-mark')} width={markSize} height={markSize} alt=""/>
        <span className="logo-wordmark">screensort</span>
    </span>
}
