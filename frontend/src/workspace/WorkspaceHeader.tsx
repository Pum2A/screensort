import {ArrowUpRight, ChevronRight, Images, LockKeyhole} from 'lucide-react'
import {AnimatePresence} from 'motion/react'
import * as m from 'motion/react-m'
import {folderName} from '../catalog/format'
import {UpdatePrompt} from '../desktop/updates/UpdatePrompt'
import type {WorkspaceView} from './state'

type Props = {view: WorkspaceView; folder: string; total: number | null; onAnalyze: () => void}

export function WorkspaceHeader({view, folder, total, onAnalyze}: Props) {
    return <>
        <header className="workspace-topbar">
            <nav aria-label="Lokalizacja w aplikacji"><span>Przestrzeń osobista</span><ChevronRight size={14}/>
                <strong>{view === 'catalog' ? 'Biblioteka' : 'Analiza i porządek'}</strong></nav>
            <div className="topbar-actions"><span className="local-badge"><LockKeyhole size={13}/>Tylko na tym komputerze</span><UpdatePrompt/></div>
        </header>
        <section className="workspace-heading">
            <AnimatePresence mode="wait" initial={false}>
                <m.div key={view} className="workspace-title" initial={{opacity: 0, y: 10}} animate={{opacity: 1, y: 0}} exit={{opacity: 0, y: -6}}>
                    <p className="eyebrow">TWÓJ SCREENSORT</p>
                    <h1>{view === 'catalog' ? 'Twoje obrazy.' : 'Analiza. Potem porządek.'}</h1>
                    <p>{view === 'catalog' ? 'Przejrzyj zdjęcia, poznaj ich zawartość i zdecyduj, co z nimi dalej.' : 'Sprawdź zawartość obrazów. Popraw propozycje i zapisz uporządkowane kopie.'}</p>
                </m.div>
            </AnimatePresence>
            <div className="library-balance" aria-label="Zapisany katalog">
                <span className="balance-icon"><Images size={22}/></span>
                <div><span className="balance-label">Obrazy w katalogu</span><strong>{total === null ? '—' : total.toLocaleString('pl-PL')}</strong>
                    <span className="balance-folder" title={folder}>{folder ? folderName(folder) : 'Wybierz swój pierwszy folder'}</span></div>
                {view === 'catalog' && <button className="balance-action" aria-label="Przejdź do analizy i propozycji" onClick={onAnalyze}><ArrowUpRight size={20}/></button>}
            </div>
        </section>
    </>
}
