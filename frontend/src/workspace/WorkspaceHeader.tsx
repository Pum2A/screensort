import {LockKeyhole} from 'lucide-react'
import {UpdatePrompt} from '../desktop/updates/UpdatePrompt.js'
import type {WorkspaceView} from './state.js'

/** Figma v1+ header: a mono breadcrumb over a large title, the folder name in the library. */
export function WorkspaceHeader({view, folderLabel}: {view: WorkspaceView, folderLabel: string}) {
    const folder = folderLabel || 'Folder'
    const titles = {catalog: [`Foldery${folderLabel ? ` / ${folderLabel}` : ''}`, folderLabel || 'Biblioteka'],
        organization: ['Analiza i porządek', 'Do decyzji'], duplicates: [`${folder} / Duplikaty`, 'Duplikaty']} as const
    const [breadcrumb, title] = titles[view]
    return <header className="workspace-header">
        <div className="workspace-title">
            <span className="workspace-breadcrumb">{breadcrumb}</span>
            <h1>{title}</h1>
        </div>
        <div className="workspace-header-status">
            <span className="local-badge"><LockKeyhole size={14}/>Dane lokalne</span>
            <UpdatePrompt/>
        </div>
    </header>
}
