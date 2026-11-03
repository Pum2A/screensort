import {useContext} from 'react'
import {DesktopRuntime} from './runtimeContext.js'
import {PixelIcon} from '../ui/PixelIcon.js'

/** The engine card at the bottom of the sidebar; it opens the "Silnik AI" view. */
export function DesktopAnalysisControls({active, onOpen}: {active: boolean, onOpen: () => void}) {
    const runtime = useContext(DesktopRuntime)
    if (!runtime) return <div className="engine-card browser-mode"><span className="engine-chip"><PixelIcon name="cpu"/></span><div><strong>Tryb przeglądarkowy</strong><small>usługi osobno</small></div></div>
    const {status} = runtime
    const label = status.analysisReady ? 'gotowy · lokalnie' : status.analysisStarting ? 'uruchamiamy…' : status.error ? 'wymaga uwagi' : 'na żądanie'
    return <>
        <button className="engine-card" data-state={status.error ? 'error' : status.analysisReady ? 'ready' : 'idle'} onClick={onOpen}
            aria-current={active ? 'page' : undefined}>
            <span className="engine-chip"><PixelIcon name="cpu"/></span>
            <span><strong>Silnik AI</strong><small>{label}</small></span>
            {status.analysisStarting && <span className="engine-progress" aria-hidden="true"><i/></span>}
        </button>
    </>
}
