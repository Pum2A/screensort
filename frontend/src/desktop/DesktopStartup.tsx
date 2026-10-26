import {useEffect, useReducer} from 'react'
import type {ReactNode} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {CircleAlert} from 'lucide-react'
import {isDesktop} from './transport.js'
import {AnalysisReadiness} from './readiness.js'
import {DesktopRuntime} from './runtimeContext.js'
import {StartupScreen} from './StartupScreen.js'
import {watchNative} from './nativeStatus.js'
import {initialStartup, nextStartup, startupView} from './status.js'
import type {DesktopStatus} from './status.js'
import './DesktopStartup.css'

export function DesktopStartup({children}: {children: ReactNode}) {
    const [state, dispatch] = useReducer(nextStartup, initialStartup)
    useEffect(() => {
        if (!isDesktop()) return
        return watchNative<DesktopStatus>('desktop_status', 'desktop-status',
            status => dispatch({kind: 'status', status}), () => dispatch({kind: 'failed'}))
    }, [])

    if (!isDesktop()) return children
    const view = startupView(state)
    if (view.screen === 'startup' || !view.status) return <StartupScreen status={view.status}/>
    const status = view.status
    // After the first successful start a problem is a banner above the app, never a replaced app.
    return <DesktopRuntime.Provider value={{status, start: () => {
        void invoke('start_analysis_runtime').catch(cause => dispatch({kind: 'status', status: {...status,
            error: typeof cause === 'string' ? cause : 'Nie udało się zlecić uruchomienia silnika analizy.'}}))
    }}}><AnalysisReadiness.Provider value={status.analysisReady && !view.connectionLost}>
        {view.connectionLost && <div className="feedback feedback-warning connection-banner" role="alert">
            <CircleAlert size={19}/><div><strong>Utraciliśmy połączenie z lokalnym backendem</strong>
                <p>Ponawiamy próbę. Twoje dane są bezpieczne. Jeśli to potrwa dłużej, zamknij i uruchom aplikację ponownie.</p></div>
        </div>}
        {children}
    </AnalysisReadiness.Provider></DesktopRuntime.Provider>
}
