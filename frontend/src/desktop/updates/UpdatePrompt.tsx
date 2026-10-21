import {useContext, useRef, useState} from 'react'
import {AnimatePresence} from 'motion/react'
import {ArrowDownToLine, Check, CircleAlert, Download, LoaderCircle, RefreshCw, RotateCw, X} from 'lucide-react'
import {Modal} from '../../ui/Modal'
import {AppUpdates} from './context'
import type {UpdateContextValue} from './context'
import {installationBlocker, updateLabel, updatePending, updatePercent} from './state'
import {Tooltip} from '../../ui/Tooltip'
import {packageSize} from '../packages/state'
import './updates.css'

export function UpdateDetails({context}: {context: UpdateContextValue}) {
    const {status, error, reasons, check, download, install, restart} = context
    const blocked = installationBlocker(reasons, status)
    const progress = updatePercent(status)
    return <div className="update-details">
        <p>ScreenSort sprawdza nowe wersje przy uruchomieniu. Pobieranie i instalacja odbywają się tutaj — bez szukania plików na GitHubie.</p>
        <div className="update-version"><span>Twoja wersja <strong>{status.currentVersion || '…'}</strong></span>{status.version && <span>Dostępna <strong>{status.version}</strong></span>}</div>
        {status.phase === 'DEVELOPMENT' ? <div className="feedback feedback-warning">To uruchomienie developerskie. Nie zastępujemy go instalatorem; aktualizacje działają w zainstalowanej wersji.</div>
            : status.phase === 'CHECKING' ? <p role="status" className="inline-loading"><LoaderCircle size={18} className="spin"/>Sprawdzamy dostępne wersje…</p>
                : status.phase === 'UP_TO_DATE' ? <p className="feedback feedback-success" role="status"><Check size={18}/>Masz aktualną wersję.</p> : null}
        {status.notes && <details><summary>Co nowego w tej wersji?</summary><p className="update-notes">{status.notes}</p></details>}
        {status.phase === 'DOWNLOADING' && <div role="status"><strong>Pobieramy i sprawdzamy podpis…</strong><progress max="100" value={progress} aria-label="Pobieranie aktualizacji"/><small>{packageSize(status.downloadedBytes)}{status.totalBytes ? ' z ' + packageSize(status.totalBytes) : ''}</small></div>}
        {status.phase === 'DOWNLOADED' && <div className="feedback feedback-success"><Check size={18}/><p>Aktualizacja jest pobrana, a jej podpis potwierdzony. Instalacja zamknie ScreenSort i uruchomi nową wersję. Baza, zdjęcia i osobno pobrany silnik analizy pozostają na miejscu.</p></div>}
        {status.phase === 'DOWNLOADED' && blocked && <p className="feedback feedback-warning" role="status">{blocked}</p>}
        {status.phase === 'INSTALLING' && <p role="status" className="inline-loading"><LoaderCircle size={18} className="spin"/>Zatrzymujemy lokalne usługi i uruchamiamy instalator…</p>}
        {(error || status.error) && <div className="feedback feedback-error" role="alert"><CircleAlert size={18}/><p>{error ?? status.error}</p></div>}
        <div className="button-row">
            {(status.phase === 'AVAILABLE' || status.phase === 'ERROR' && status.version) && <button className="primary-button" onClick={download}><Download size={17}/>Pobierz aktualizację</button>}
            {status.phase === 'DOWNLOADED' && <button className="primary-button" disabled={!!blocked} onClick={install}><RotateCw size={17}/>Instaluj i uruchom ponownie</button>}
            {status.phase === 'RESTART_REQUIRED' && <button className="primary-button" onClick={restart}><RotateCw size={17}/>Uruchom ScreenSort ponownie</button>}
            {['IDLE', 'ERROR', 'UP_TO_DATE', 'AVAILABLE'].includes(status.phase) && <button className="secondary-button" onClick={check}><RefreshCw size={16}/>Sprawdź ponownie</button>}
        </div>
        <small>Połączenie służy wyłącznie do sprawdzenia wersji i pobrania programu. Nie wysyłamy zdjęć ani zawartości katalogu. Modele analizy nie są ponownie dołączane do aktualizacji Core.</small>
    </div>
}

export function UpdatePrompt() {
    const context = useContext(AppUpdates)
    const [open, setOpen] = useState(false)
    const [dismissed, setDismissed] = useState<string | null>(null)
    const trigger = useRef<HTMLButtonElement>(null)
    if (!context) return null
    const {status} = context
    const attention = ['AVAILABLE', 'DOWNLOADED', 'DOWNLOADING', 'INSTALLING', 'RESTART_REQUIRED'].includes(status.phase)
    return <>
        <div className="app-update-anchor"><Tooltip label="Aktualizacje sprawdzane przy każdym uruchomieniu">
            <button ref={trigger} className="update-settings" data-phase={status.phase} onClick={() => setOpen(true)} aria-haspopup="dialog">
                {updatePending(status.phase) ? <LoaderCircle size={14} className="spin"/> : status.phase === 'UP_TO_DATE' ? <Check size={14}/> : status.phase === 'ERROR' ? <CircleAlert size={14}/> : <ArrowDownToLine size={14}/>}
                <span aria-live="polite">{updateLabel(status)}</span>{attention && <span className="update-dot"/>}
            </button></Tooltip></div>
        {attention && !open && dismissed !== status.version && <aside className="app-update-toast" aria-label="Aktualizacja aplikacji"><Download size={20}/><div><strong>{status.phase === 'DOWNLOADED' ? 'Gotowe do instalacji' : status.phase === 'DOWNLOADING' ? 'Pobieramy aktualizację' : 'Nowa wersja ScreenSort'}</strong><p>{status.version}</p><button className="text-button" onClick={() => setOpen(true)}>Pokaż aktualizację</button></div><button className="icon-button" aria-label="Przypomnij po ponownym uruchomieniu" onClick={() => setDismissed(status.version)}><X size={16}/></button></aside>}
        <AnimatePresence>{open && <Modal title="Zawsze na bieżąco." subtitle="AKTUALIZACJE SCREENSORT" trigger={trigger} onClose={() => setOpen(false)}><UpdateDetails context={context}/></Modal>}</AnimatePresence>
    </>
}
