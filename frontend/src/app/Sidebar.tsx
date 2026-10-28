import {DesktopAnalysisControls} from '../tauri/DesktopAnalysisControls.js'
import {Logo} from '../ui/Logo.js'
import {PixelIcon} from '../ui/PixelIcon.js'
import type {PixelIconName} from '../ui/pixelIcons.js'
import type {WorkspaceView} from './state.js'
import './Sidebar.css'

type Props = {
    view: WorkspaceView
    onView: (view: WorkspaceView) => void
    /** total is null until the library has loaded. */
    counts: {total: number | null, pending: number, duplicates: number}
    folder: string
    folderLabel: string
    /** Opens the folder picker; absent outside the desktop shell. */
    onAddFolder?: () => void
    locked: boolean
}

const items: {view: WorkspaceView, label: string, icon: PixelIconName}[] = [
    {view: 'catalog', label: 'Biblioteka', icon: 'images'},
    {view: 'organization', label: 'Do decyzji', icon: 'inbox'},
    {view: 'duplicates', label: 'Duplikaty', icon: 'layers'},
]

/** Figma v1+ sidebar: views, the folder being worked on, and the AI engine at the bottom. */
export function Sidebar({view, onView, counts, folder, folderLabel, onAddFolder, locked}: Props) {
    return <aside className="sidebar">
        <a className="sidebar-brand" href="#catalog" aria-label="ScreenSort" onClick={event => {event.preventDefault(); onView('catalog')}}>
            <Logo/>
        </a>
        <nav className="sidebar-nav" aria-label="Widoki aplikacji">
            {items.map(item => <button key={item.view} className="sidebar-item" aria-current={view === item.view ? 'page' : undefined} onClick={() => onView(item.view)}>
                <span className="sidebar-chip"><PixelIcon name={item.icon}/></span>
                <span className="sidebar-label">{item.label}</span>
                {item.view === 'catalog' && counts.total !== null && <span className="sidebar-count">{counts.total}</span>}
                {item.view === 'organization' && counts.pending > 0 && <span className="sidebar-count sidebar-count-lime">{counts.pending}</span>}
                {item.view === 'duplicates' && counts.duplicates > 0 && <span className="sidebar-count">{counts.duplicates}</span>}
            </button>)}
        </nav>
        <p className="sidebar-section">FOLDERY</p>
        {folder && <div className="sidebar-folder" aria-current="true" title={folder}>
            <span className="sidebar-chip" aria-hidden="true"><PixelIcon name="folder"/></span>
            <span className="sidebar-folder-text"><strong>{folderLabel}</strong>{counts.total !== null && <small>{counts.total} obrazów</small>}</span>
        </div>}
        {onAddFolder && <button className="sidebar-add" disabled={locked} onClick={onAddFolder}>
            <span className="sidebar-chip sidebar-chip-dashed"><PixelIcon name="plus"/></span>{folder ? 'Zmień folder' : 'Dodaj folder'}
        </button>}
        <div className="sidebar-spacer"/>
        <DesktopAnalysisControls/>
    </aside>
}
