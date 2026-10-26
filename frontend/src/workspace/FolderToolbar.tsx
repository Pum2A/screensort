import {FolderOpen, LoaderCircle, ScanLine} from 'lucide-react'

/** The source folder field with "choose/open" and "scan". Desktop picks folders through the shell. */
export function FolderToolbar({desktop, folderPath, locked, loading, scanning, onFolderPath, onChoose, onOpen, onScan}: {
    desktop: boolean, folderPath: string, locked: boolean, loading: boolean, scanning: boolean,
    onFolderPath: (value: string) => void, onChoose: () => void, onOpen: () => void, onScan: () => void,
}) {
    return <section className="folder-toolbar" aria-label="Wybór i skanowanie folderu">
        <div className="folder-field"><span className="folder-symbol"><FolderOpen size={24}/></span><label htmlFor="folder-path">Folder źródłowy
            <input id="folder-path" type="text" value={folderPath} onChange={event => onFolderPath(event.target.value)} readOnly={desktop} disabled={locked}
                placeholder={desktop ? 'Wybierz folder z obrazami' : 'Wpisz ścieżkę folderu'} title={folderPath}/>
        </label></div>
        <div className="folder-actions">{desktop
            ? <button className="secondary-button" disabled={locked} onClick={onChoose}><FolderOpen size={17}/>Wybierz folder</button>
            : <button className="secondary-button" disabled={locked || loading || !folderPath.trim()} onClick={onOpen}><FolderOpen size={17}/>Otwórz katalog</button>}
            <button className="primary-button" disabled={locked || !folderPath.trim()} onClick={onScan}>
                {scanning ? <LoaderCircle size={17} className="spin"/> : <ScanLine size={17}/>}{scanning ? 'Skanujemy…' : 'Skanuj folder'}</button>
        </div>
    </section>
}
