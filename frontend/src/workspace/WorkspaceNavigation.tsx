import {Images, Sparkles} from 'lucide-react'
import {DesktopAnalysisControls} from '../desktop/DesktopAnalysisControls.js'
import {Logo} from '../ui/Logo.js'
import type {WorkspaceView} from './state.js'

type Props = {total: number | null; view: WorkspaceView; onView: (view: WorkspaceView) => void}

export function WorkspaceNavigation({total, view, onView}: Props) {
    return <header className="workspace-navigation-bar">
        <a className="brand" href="#catalog" aria-label="ScreenSort" onClick={event => {event.preventDefault(); onView('catalog')}}>
            <Logo/>
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
