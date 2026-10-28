import {useState} from 'react'
import {MotionProvider} from '../../src/ui/MotionProvider.js'
import {AppUpdates} from '../../src/tauri/updates/context.js'
import {UpdatePrompt} from '../../src/tauri/updates/UpdatePrompt.js'
import type {UpdateStatus} from '../../src/tauri/updates/state.js'

export function UpdateFixture() {
    const [phase, setPhase] = useState<UpdateStatus['phase']>('AVAILABLE')
    const [dirty, setDirty] = useState(false)
    const [message, setMessage] = useState('')
    const status: UpdateStatus = {phase, currentVersion: '0.2.1', version: '0.2.2', notes: 'Nowy eksplorator, ochrona edycji i aktualizacje w aplikacji.', downloadedBytes: 48 * 1024 * 1024, totalBytes: 345 * 1024 * 1024, error: phase === 'ERROR' ? 'Testowy błąd weryfikacji. Nie zmieniono aplikacji ani danych.' : null}
    return <MotionProvider><AppUpdates.Provider value={{status, error: null, reasons: dirty ? ['Zapisz lub odrzuć edycję przed restartem.'] : [], setBlocker: () => {},
        check: () => setPhase('UP_TO_DATE'), download: () => setPhase('DOWNLOADING'), install: () => setMessage('Symulacja: w produkcji dopiero teraz następuje zatrzymanie usług i uruchomienie instalatora.'), restart: () => setMessage('Symulacja restartu — niczego nie uruchomiono.')}}>
        <main style={{maxWidth: 700, margin: '40px auto', padding: 24}}><h1>Test UI aktualizacji</h1><p>WYŁĄCZNIE SYMULACJA. Brak sieci, bazy i uruchamiania instalatora.</p>
            <label>Scenariusz <select value={phase} onChange={event => setPhase(event.target.value as UpdateStatus['phase'])}>{['AVAILABLE', 'DOWNLOADING', 'DOWNLOADED', 'ERROR', 'UP_TO_DATE', 'RESTART_REQUIRED'].map(value => <option key={value}>{value}</option>)}</select></label>
            <p><label><input type="checkbox" checked={dirty} onChange={event => setDirty(event.target.checked)}/>Niezapisana edycja (symulacja)</label></p><p role="status">{message}</p>
        </main><UpdatePrompt/>
    </AppUpdates.Provider></MotionProvider>
}
