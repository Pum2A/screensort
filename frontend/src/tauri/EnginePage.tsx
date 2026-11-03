import {useContext} from 'react'
import {useAutoAnalysis} from '../analysis/autoAnalysis.js'
import {DesktopRuntime} from './runtimeContext.js'
import {EngineView} from './EngineView.js'
import {useAiPackages} from './packages/useAiPackages.js'

/** The "Silnik AI" view: native package state and engine status wired into the Figma layout. */
export function EnginePage() {
    const runtime = useContext(DesktopRuntime)
    const autoAnalysis = useAutoAnalysis()
    if (!runtime) return <EngineView status={null} runtime={null} requesting={false} error={null} speed={null}
        autoAnalysis={autoAnalysis} onStart={() => undefined} onInstall={() => undefined} onCancel={() => undefined}/>
    return <DesktopEngine runtime={runtime} autoAnalysis={autoAnalysis}/>
}

function DesktopEngine({runtime, autoAnalysis}: {runtime: NonNullable<React.ContextType<typeof DesktopRuntime>>,
    autoAnalysis: ReturnType<typeof useAutoAnalysis>}) {
    const packages = useAiPackages()
    const {status} = runtime
    return <EngineView status={packages.status} requesting={packages.requesting} error={packages.error} speed={packages.speed}
        runtime={{ready: status.analysisReady, starting: status.analysisStarting, error: status.error}}
        autoAnalysis={autoAnalysis} onStart={runtime.start} onInstall={(id, fromFile) => void packages.install(id, fromFile)}
        onCancel={packages.cancel}/>
}
