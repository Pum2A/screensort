import {Images, ScanLine, Sparkles} from 'lucide-react'
import {DesktopAnalysisControls} from '../desktop/DesktopAnalysisControls'
import type {WorkspaceView} from './state'

type Props = {total: number | null; view: WorkspaceView; onView: (view: WorkspaceView) => void}

export function WorkspaceNavigation({total, view, onView}: Props) {
    return <header className="workspace-navigation-bar">
        <a className="brand" href="#catalog" onClick={event => {event.preventDefault(); onView('catalog')}}>
            <span className="brand-mark"><ScanLine size={23}/></span><span>ScreenSort<span className="brand-version">TWOJE OBRAZY. TWÓJ PORZĄDEK.</span></span>
        </a>
        <nav className="workspace-navigation" aria-label="Widoki aplikacji">
            <button className="workspace-nav-item" aria-current={view === 'catalog' ? 'page' : undefined} onClick={() => onView('catalog')}>
                <Images size={18}/><span>Biblioteka</span>{total !== null && <span className="nav-count">{total}</span>}
            </button>
            <button className="workspace-nav-item" aria-current={view === 'organization' ? 'page' : undefined} onClick={() => onView('organization')}>
                <Sparkles size={18}/><span>Analiza i porządek</span>
            </button>
        </nav>
        <div className="navigation-engine"><DesktopAnalysisControls/></div>
    </header>
}
