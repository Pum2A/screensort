import {LockKeyhole} from 'lucide-react'
import {UpdatePrompt} from '../desktop/updates/UpdatePrompt'
import type {WorkspaceView} from './state'

export function WorkspaceHeader({view}: {view: WorkspaceView}) {
    return <header className="workspace-header">
        <div className="workspace-title">
            <h1>{view === 'catalog' ? 'Biblioteka' : 'Analiza i porządek'}</h1>
            <p>{view === 'catalog'
                ? 'Przeglądaj obrazy i sprawdzaj ich szczegóły.'
                : 'Przeanalizuj zawartość. Sprawdź nazwy i kategorie przed zapisaniem kopii.'}</p>
        </div>
        <div className="workspace-header-status">
            <span className="local-badge"><LockKeyhole size={14}/>Dane lokalne</span>
            <UpdatePrompt/>
        </div>
    </header>
}
