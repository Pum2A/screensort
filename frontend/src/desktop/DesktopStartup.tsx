import {useEffect, useState} from 'react'
import type {ReactNode} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {isDesktop} from './transport'
import {AnalysisReadiness} from './readiness'
import {DesktopRuntime} from './runtimeContext'
import {StartupScreen} from './StartupScreen'
import type {DesktopStatus} from './status'
import './DesktopStartup.css'

export function DesktopStartup({children}: {children: ReactNode}) {
    const [status, setStatus] = useState<DesktopStatus | null>(null)
    useEffect(() => {
        if (!isDesktop()) return
        let stopped = false
        let timer: ReturnType<typeof setTimeout>
        async function check() {
            try {
                const next = await invoke<DesktopStatus>('desktop_status')
                if (!stopped) setStatus(next)
            } catch {
                if (!stopped) setStatus({phase: 'Błąd uruchamiania', backendReady: false, analysisReady: false,
                    analysisStarting: false, error: 'Nie udało się odczytać stanu aplikacji. Zamknij ją i spróbuj ponownie.'})
            }
            if (!stopped) timer = setTimeout(() => void check(), 1000)
        }
        void check()
        return () => { stopped = true; clearTimeout(timer) }
    }, [])

    if (!isDesktop()) return children
    if (!status?.backendReady) return <StartupScreen status={status}/>
    return <DesktopRuntime.Provider value={{status, start: () => {
        void invoke('start_analysis_runtime').catch(cause => setStatus(current => current && ({...current,
            error: typeof cause === 'string' ? cause : 'Nie udało się zlecić uruchomienia silnika analizy.'})))
    }}}><AnalysisReadiness.Provider value={status.analysisReady}>{children}</AnalysisReadiness.Provider></DesktopRuntime.Provider>
}
