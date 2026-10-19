import {useEffect, useState} from 'react'
import type {ReactNode} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {Icon} from '../ui/Icon'
import {isDesktop} from './transport'
import {AnalysisReadiness} from './readiness'
import './DesktopStartup.css'

type Status = {phase: string, backendReady: boolean, analysisReady: boolean, analysisStarting: boolean, error: string | null}

export function DesktopStartup({children}: {children: ReactNode}) {
    const [status, setStatus] = useState<Status | null>(null)
    useEffect(() => {
        if (!isDesktop()) return
        let stopped = false
        let timer: ReturnType<typeof setTimeout>
        async function check() {
            try {
                const next = await invoke<Status>('desktop_status')
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
    if (!status?.backendReady) return <main className="desktop-startup" aria-live="polite">
        <Icon name={status?.error ? 'shield' : 'refresh'} className={status?.error ? '' : 'spin'}/>
        <h1>ScreenSort</h1><h2>{status?.phase ?? 'Przygotowujemy aplikację…'}</h2>
        <p>{status?.error ?? 'Uruchamiamy katalog i lokalną bazę. Modele AI nie są teraz ładowane.'}</p>
        {status?.error && <p>Nie usuwaj katalogu danych. Szczegóły znajdują się w lokalnych logach aplikacji.</p>}
    </main>
    return <AnalysisReadiness.Provider value={status.analysisReady}>{!status.analysisReady && <div className="desktop-notice" role="status">
        <Icon name={status.error ? 'shield' : 'image'} className={status.analysisStarting ? 'spin' : ''}/>
        {status.error ?? status.phase}
        <button type="button" className="secondary-button" disabled={status.analysisStarting}
            onClick={() => void invoke('start_analysis_runtime').catch(() => setStatus({...status,
                error: 'Nie udało się zlecić uruchomienia AI. Spróbuj ponownie.'}))}>
            {status.analysisStarting ? 'Przygotowywanie AI…' : 'Uruchom pakiet AI'}
        </button>
    </div>}{children}</AnalysisReadiness.Provider>
}
