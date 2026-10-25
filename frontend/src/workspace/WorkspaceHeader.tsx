import {LockKeyhole} from 'lucide-react'
import {UpdatePrompt} from '../desktop/updates/UpdatePrompt.js'
import type {WorkspaceView} from './state.js'

/** Figma v1+ header: a mono breadcrumb over a large title, the folder name in the library. */
export function WorkspaceHeader({view, folderLabel}: {view: WorkspaceView, folderLabel: string}) {
    const catalog = view === 'catalog'
    return <header className="workspace-header">
        <div className="workspace-title">
            <span className="workspace-breadcrumb">{catalog ? `Foldery${folderLabel ? ` / ${folderLabel}` : ''}` : 'Analiza i porządek'}</span>
            <h1>{catalog ? folderLabel || 'Biblioteka' : 'Do decyzji'}</h1>
        </div>
        <div className="workspace-header-status">
            <span className="local-badge"><LockKeyhole size={14}/>Dane lokalne</span>
            <UpdatePrompt/>
        </div>
    </header>
}
