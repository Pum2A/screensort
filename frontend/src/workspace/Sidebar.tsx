import {DesktopAnalysisControls} from '../desktop/DesktopAnalysisControls.js'
import {Logo} from '../ui/Logo.js'
import {PixelIcon} from '../ui/PixelIcon.js'
import {pixelIconUrl} from '../ui/pixelIcons.js'
import type {PixelIconName} from '../ui/pixelIcons.js'
import type {WorkspaceView} from './state.js'
import './Sidebar.css'

type Props = {
    view: WorkspaceView
    onView: (view: WorkspaceView) => void
    total: number | null
    folder: string
    folderLabel: string
    /** Opens the folder picker; absent outside the desktop shell. */
    onAddFolder?: () => void
    locked: boolean
}

const items: {view: WorkspaceView, label: string, icon: PixelIconName}[] = [
    {view: 'catalog', label: 'Biblioteka', icon: 'images'},
    {view: 'organization', label: 'Do decyzji', icon: 'inbox'},
]

/** Figma v1+ sidebar: views, the folder being worked on, and the AI engine at the bottom. */
export function Sidebar({view, onView, total, folder, folderLabel, onAddFolder, locked}: Props) {
    return <aside className="sidebar">
        <a className="sidebar-brand" href="#catalog" aria-label="ScreenSort" onClick={event => {event.preventDefault(); onView('catalog')}}>
            <Logo/>
        </a>
        <nav className="sidebar-nav" aria-label="Widoki aplikacji">
            {items.map(item => <button key={item.view} className="sidebar-item" aria-current={view === item.view ? 'page' : undefined} onClick={() => onView(item.view)}>
                <span className="sidebar-chip"><PixelIcon name={item.icon}/></span>
                <span className="sidebar-label">{item.label}</span>
                {item.view === 'catalog' && total !== null && <span className="sidebar-count">{total}</span>}
            </button>)}
        </nav>
        <p className="sidebar-section">FOLDERY</p>
        {folder && <div className="sidebar-folder" aria-current="true" title={folder}>
            <span className="folder-mosaic" aria-hidden="true">{[0, 1, 2, 3].map(index => <img key={index} src={pixelIconUrl('placeholder')} alt=""/>)}</span>
            <span className="sidebar-folder-text"><strong>{folderLabel}</strong>{total !== null && <small>{total} obrazów</small>}</span>
        </div>}
        {onAddFolder && <button className="sidebar-add" disabled={locked} onClick={onAddFolder}>
            <span className="sidebar-chip sidebar-chip-dashed"><PixelIcon name="plus"/></span>{folder ? 'Zmień folder' : 'Dodaj folder'}
        </button>}
        <div className="sidebar-spacer"/>
        <DesktopAnalysisControls/>
    </aside>
}
