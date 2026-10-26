import {useRef, useState} from 'react'
import {AnimatePresence} from 'motion/react'
import App from '../../src/App.js'
import {MotionProvider} from '../../src/ui/MotionProvider.js'
import {QueryProvider} from '../../src/shared/QueryProvider.js'
import {Modal} from '../../src/ui/Modal.js'
import {StartupScreen} from '../../src/desktop/StartupScreen.js'
import {AnalysisEngineContent} from '../../src/desktop/packages/AnalysisEngineContent.js'
import type {PackageStatus} from '../../src/desktop/packages/state.js'
import {AppUpdates} from '../../src/desktop/updates/context.js'
import type {UpdateStatus} from '../../src/desktop/updates/state.js'
import type {ReactNode} from 'react'

/** Synthetic update states only: these buttons never download or install anything. */
function FixtureUpdates({scenario, children}: {scenario: string; children: ReactNode}) {
    const [status, setStatus] = useState<UpdateStatus>({phase: scenario === 'updates' ? 'AVAILABLE' : 'UP_TO_DATE',
        currentVersion: '0.3.0', version: scenario === 'updates' ? '0.3.1' : null, notes: null,
        downloadedBytes: 0, totalBytes: null, error: null})
    return <AppUpdates.Provider value={{status, error: null, reasons: [], setBlocker: () => {},
        check: () => setStatus(current => ({...current, phase: 'UP_TO_DATE', version: null})),
        download: () => setStatus(current => ({...current, phase: 'DOWNLOADED'})), install: () => {}, restart: () => {}}}>{children}</AppUpdates.Provider>
}

function EngineFixture({scenario}: {scenario: string}) {
    const [visible, setVisible] = useState(true)
    const [starting, setStarting] = useState(scenario === 'warmup')
    const [downloading, setDownloading] = useState(false)
    const trigger = useRef<HTMLButtonElement>(null)
    const status: PackageStatus = {bundled: scenario === 'warmup' || scenario === 'engine-error', packages: [{installed: false, package: {id: 'fixture', name: 'Profil CPU', version: 'dane-testowe', archiveBytes: 747507987, unpackedBytes: 1365180817, url: 'https://example.invalid/never-requested'}}],
        progress: {phase: downloading ? 'DOWNLOADING' : 'PREPARING', busy: downloading, completedBytes: 247507987, totalBytes: 747507987, error: null}}
    return <div style={{padding: 40}}><button ref={trigger} className="primary-button" onClick={() => setVisible(true)}>Otwórz ustawienia silnika</button>
        <AnimatePresence>{visible && <Modal title="Analiza na Twoim komputerze" subtitle="SILNIK ANALIZY" trigger={trigger} onClose={() => setVisible(false)}>
            <AnalysisEngineContent status={status} starting={starting} ready={false} requesting={false}
                error={scenario === 'engine-error' ? 'Serwis analizy nie zgłosił gotowości. Katalog nadal działa.' : null}
                onStart={() => setStarting(true)} onInstall={() => setDownloading(true)} onCancel={() => setDownloading(false)}/>
        </Modal>}</AnimatePresence></div>
}

export function WorkspacePreview({scenario, mobile, onConflict}: {scenario: string; mobile: boolean; onConflict: () => void}) {
    const modes = [['catalog', 'Katalog'], ['organized', 'Porządkowanie'], ['paused', 'Pauza 7/94'], ['startup', 'Start aplikacji'], ['engine', 'Pobieranie silnika'], ['warmup', 'Ładowanie modeli'], ['engine-error', 'Błąd silnika'], ['updates', 'Aktualizacje']]
    return <QueryProvider><MotionProvider><FixtureUpdates scenario={scenario}><div className="fixture-toolbar" style={{display: 'flex', flexWrap: 'wrap', gap: '5px 15px', alignItems: 'center', background: '#fff5d9', color: '#816523', borderBottom: '1px solid #e4d7af', padding: '8px 14px', font: '11px Segoe UI, sans-serif'}}>
        <strong>PODGLĄD UI · DANE FIKCYJNE</strong>{modes.map(([mode, label]) => <a key={mode} href={'?scenario=' + mode}>{label}</a>)}<a href="?mobile=1">Telefon 390 px</a><button onClick={onConflict}>Symuluj konflikt #1</button><span>Brak połączenia z prywatną bazą i modelami.</span></div>
        {mobile ? <iframe title="ScreenSort na telefonie" src="?frame=1" style={{display: 'block', width: 390, maxWidth: '100%', height: 1100, margin: '20px auto', border: '1px solid #d8dfcf'}}/>
            : scenario === 'startup' ? <StartupScreen status={{phase: 'Uruchamiamy backend i sprawdzamy migracje…', backendReady: false, analysisReady: false, analysisStarting: false, error: null}}/>
                : ['engine', 'warmup', 'engine-error'].includes(scenario) ? <EngineFixture scenario={scenario}/> : <App initialFolder="synthetic-fixture"/>}
    </FixtureUpdates></MotionProvider></QueryProvider>
}
