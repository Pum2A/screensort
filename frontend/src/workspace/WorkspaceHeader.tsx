import {FolderOpen, Images, LoaderCircle, LockKeyhole, ScanLine, Sparkles} from 'lucide-react'
import {AnimatePresence} from 'motion/react'
import * as m from 'motion/react-m'
import {folderName} from '../catalog/format'
import {UpdatePrompt} from '../desktop/updates/UpdatePrompt'
import type {WorkspaceView} from './state'

type Props = {view: WorkspaceView; folder: string; total: number | null; onAnalyze: () => void;
    onChoose: () => void; onScan: () => void; locked: boolean; canScan: boolean; scanning: boolean}

export function WorkspaceHeader({view, folder, total, onAnalyze, onChoose, onScan, locked, canScan, scanning}: Props) {
    return <div className="workspace-hero">
        <header className="workspace-topbar">
            <span className="hero-context"><Images size={16}/>{view === 'catalog' ? 'Twoja biblioteka' : 'Analiza i porządek'}</span>
            <div className="topbar-actions"><span className="local-badge"><LockKeyhole size={13}/>Tylko na tym komputerze</span><UpdatePrompt/></div>
        </header>
        <section className="workspace-heading">
            <AnimatePresence mode="wait" initial={false}>
                <m.div key={view} className="workspace-title" initial={{opacity: 0, y: 10}} animate={{opacity: 1, y: 0}} exit={{opacity: 0, y: -6}}>
                    <h1>{view === 'catalog' ? 'Wszystkie obrazy. Jedno miejsce.' : 'Od obrazu do porządku.'}</h1>
                </m.div>
            </AnimatePresence>
            <div className="library-balance" aria-label="Zapisany katalog">
                <div><span className="balance-label">Obrazy w katalogu</span><strong>{total === null ? '—' : total.toLocaleString('pl-PL')}</strong>
                    <span className="balance-folder" title={folder}>{folder ? folderName(folder) : 'Wybierz swój pierwszy folder'}</span></div>
            </div>
            <div className="hero-actions" aria-label="Szybkie działania">
                <button disabled={locked} onClick={onChoose} aria-label="Zmień źródło obrazów"><span><FolderOpen size={23}/></span>Folder</button>
                <button disabled={locked || !canScan} onClick={onScan} aria-label="Rozpocznij skanowanie"><span>{scanning ? <LoaderCircle size={23} className="spin"/> : <ScanLine size={23}/>}</span>{scanning ? 'Skanujemy' : 'Skanuj'}</button>
                <button onClick={onAnalyze} aria-label="Przejdź do analizy i propozycji"><span><Sparkles size={23}/></span>Analizuj</button>
            </div>
        </section>
    </div>
}
