import {ArrowUpRight, CheckCheck, ChevronDown, FolderOpen, FolderTree, Images, ScanLine, ShieldCheck, Workflow} from 'lucide-react'
import {folderName} from '../catalog/format'
import {DesktopAnalysisControls} from '../desktop/DesktopAnalysisControls'
import type {WorkspaceView} from './state'

type Props = {folder: string; total: number | null; view: WorkspaceView; onView: (view: WorkspaceView) => void;
    onChoose: () => void; desktop: boolean; locked: boolean}

export function ExplorerSidebar({folder, total, view, onView, onChoose, desktop, locked}: Props) {
    return <aside className="explorer-sidebar" aria-label="Nawigacja eksploratora">
        <a className="brand" href="#catalog" onClick={event => {event.preventDefault(); onView('catalog')}}>
            <span className="brand-mark"><ScanLine size={24}/></span><span>ScreenSort<span className="brand-version">PRYWATNA PRZESTRZEŃ</span></span>
        </a>
        <div className="sidebar-section-label">TWOJA PRZESTRZEŃ</div>
        <nav className="workspace-navigation" aria-label="Widoki aplikacji">
            <button className="workspace-nav-item" aria-current={view === 'catalog' ? 'page' : undefined} onClick={() => onView('catalog')}>
                <Images size={18}/><span>Katalog obrazów</span>{total !== null && <span className="nav-count">{total}</span>}
            </button>
            <button className="workspace-nav-item" aria-current={view === 'organization' ? 'page' : undefined} onClick={() => onView('organization')}>
                <Workflow size={18}/><span>Analiza i propozycje</span><ArrowUpRight size={14}/>
            </button>
        </nav>
        <div className="sidebar-source">
            <div className="sidebar-section-label">FOLDER ŹRÓDŁOWY</div>
            <div className="source-tree"><div className="source-tree-root"><ChevronDown size={14}/><FolderOpen size={17}/>
                <strong title={folder}>{folder ? folderName(folder) : 'Nie wybrano folderu'}</strong></div>
                <button className="source-tree-child" onClick={() => onView('catalog')}><Images size={15}/>Obrazy<span>{total ?? '—'}</span></button>
            </div>
            <p className="source-tree-note">Bezpośrednia zawartość wybranego folderu.</p>
            {desktop && <button type="button" className="secondary-button sidebar-choose" disabled={locked} onClick={onChoose}>
                <FolderTree size={16}/>Zmień folder
            </button>}
        </div>
        <div className="sidebar-guide"><span className="sidebar-section-label">JAK TO DZIAŁA</span>
            <div><ScanLine size={15}/><span>Zeskanuj folder</span></div><div><Workflow size={15}/><span>Przeanalizuj zawartość</span></div>
            <div><CheckCheck size={15}/><span>Sprawdź propozycje</span></div>
        </div>
        <div className="sidebar-bottom"><DesktopAnalysisControls/>
            <p className="local-note"><ShieldCheck size={15}/><span>Twoje obrazy.<br/>Na Twoim komputerze.</span></p>
        </div>
    </aside>
}
