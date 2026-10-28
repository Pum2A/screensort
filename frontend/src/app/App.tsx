import {useState} from 'react'
import {useQuery, useQueryClient} from '@tanstack/react-query'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {CircleAlert, FolderOpen, LoaderCircle, ScanLine, ShieldCheck} from 'lucide-react'
import * as m from 'motion/react-m'
import {ScanSummary} from '../gallery/reading/ScanSummary.js'
import {ScanProgress} from '../gallery/reading/ScanProgress.js'
import {useScanJob} from '../gallery/reading/useScanJob.js'
import {fetchCatalog} from '../gallery/catalogApi.js'
import {folderName} from '../gallery/format.js'
import {LibraryView} from '../gallery/LibraryView.js'
import {DuplicatesView} from '../gallery/DuplicatesView.js'
import {libraryKeys} from '../gallery/libraryApi.js'
import {duplicateGroups, isPending, libraryEntries} from '../gallery/model.js'
import {useLibrary} from '../gallery/useLibrary.js'
import {OrganizationPanel} from '../analysis/OrganizationPanel.js'
import {rejectionLabel} from '../gallery/reading/rejectionLabels.js'
import {Sidebar} from './Sidebar.js'
import {WorkspaceHeader} from './WorkspaceHeader.js'
import {storedFolder} from './state.js'
import type {WorkspaceView} from './state.js'
import './App.css'
import {useUpdateBlocker} from '../desktop/updates/context.js'

function App({initialFolder}: {initialFolder?: string} = {}) {
    const desktop = isTauri()
    const [folderPath, setFolderPath] = useState(() => initialFolder ?? storedFolder(desktop))
    const [view, setView] = useState<WorkspaceView>('catalog')
    const [organizationVisited, setOrganizationVisited] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [catalogFolder, setCatalogFolder] = useState(folderPath)
    const queryClient = useQueryClient()
    const library = useLibrary(catalogFolder)
    // The analysis needs the file count even for folders the library cannot show (over 1000 images).
    const catalogCount = useQuery({queryKey: ['catalog', 'count', catalogFolder], enabled: catalogFolder.trim() !== '',
        queryFn: ({signal}) => fetchCatalog(catalogFolder, 0, 1, signal).then(page => page.totalFiles)})
    const [editingReview, setEditingReview] = useState(false)
    const [exporting, setExporting] = useState(false)
    const scan = useScanJob(completed => {
        try {localStorage.setItem('screensort.lastFolder', completed.folderPath)} catch { /* Optional preference. */ }
        showCatalog(completed.folderPath)
    })
    const locked = scan.busy || editingReview || exporting
    useUpdateBlocker(locked, 'Zakończ skan oraz zapisz lub odrzuć edycję propozycji przed restartem.')

    function showCatalog(folder: string) {
        if (editingReview) return
        if (folder !== catalogFolder) setOrganizationVisited(false)
        setCatalogFolder(folder); setView('catalog')
        // A rescan of the same folder changes files, so the cached library must not be shown.
        void queryClient.invalidateQueries({queryKey: libraryKeys.folder(folder)})
        void queryClient.invalidateQueries({queryKey: ['catalog', 'count', folder]})
    }

    function navigate(next: WorkspaceView) {
        if (next === 'organization') setOrganizationVisited(true)
        // Decisions saved in "Do decyzji" change the library counts.
        else if (view === 'organization') void queryClient.invalidateQueries({queryKey: libraryKeys.folder(catalogFolder)})
        setView(next)
    }

    async function handleChooseFolder() {
        if (locked) return
        setError(null)
        try {
            const selected = await open({directory: true, multiple: false})
            if (typeof selected === 'string') {setFolderPath(selected); scan.clear(); showCatalog(selected)}
        } catch {setError('Nie udało się otworzyć okna wyboru folderu.')}
    }

    const entries = library.data ? libraryEntries(library.data) : []
    const counts = {total: library.data ? entries.length : null, pending: entries.filter(entry => isPending(entry.state)).length,
        duplicates: duplicateGroups(library.data?.files ?? []).length}
    const label = catalogFolder ? folderName(catalogFolder) : ''

    return <div className="app-shell">
        <Sidebar view={view} onView={navigate} counts={counts} folder={catalogFolder}
            folderLabel={label} onAddFolder={desktop ? () => void handleChooseFolder() : undefined} locked={locked}/>
        <main className="workspace-main">
            <div className="workspace-content">
                <WorkspaceHeader view={view} folderLabel={label}/>
                {/* The desktop app picks folders in the sidebar; the path field stays for the browser and the first start. */}
                {(!desktop || !catalogFolder) && <section className="folder-toolbar" aria-label="Wybór i skanowanie folderu">
                    <div className="folder-field"><span className="folder-symbol"><FolderOpen size={24}/></span><label htmlFor="folder-path">Folder źródłowy
                        <input id="folder-path" type="text" value={folderPath} onChange={event => setFolderPath(event.target.value)} readOnly={desktop} disabled={locked}
                            placeholder={desktop ? 'Wybierz folder z obrazami' : 'Wpisz ścieżkę folderu'} title={folderPath}/>
                    </label></div>
                    <div className="folder-actions">{desktop
                        ? <button className="secondary-button" disabled={locked} onClick={() => void handleChooseFolder()}><FolderOpen size={17}/>Wybierz folder</button>
                        : <button className="secondary-button" disabled={locked || library.isFetching || !folderPath.trim()} onClick={() => {scan.clear(); showCatalog(folderPath)}}><FolderOpen size={17}/>Otwórz katalog</button>}
                        <button className="primary-button" disabled={locked || !folderPath.trim()} onClick={() => {setError(null); void scan.start(folderPath)}}>
                            {scan.busy ? <LoaderCircle size={17} className="spin"/> : <ScanLine size={17}/>}{scan.busy ? 'Skanujemy…' : 'Skanuj folder'}</button>
                    </div>
                </section>}
                {editingReview && <div className="feedback feedback-warning" role="status">Masz niezapisane zmiany w przeglądzie propozycji. Zachowaliśmy je; przed zmianą folderu zapisz lub odrzuć edycję.</div>}
                {error && <div className="feedback feedback-error" role="alert"><CircleAlert size={18}/>{error}</div>}
                {scan.error && <div className="feedback feedback-error" role="alert"><CircleAlert size={18}/><div><p>{scan.error}</p>
                    {scan.job && !scan.lost && scan.job.status !== 'COMPLETED' && scan.job.status !== 'FAILED' && <button className="text-button" disabled={scan.requestActive} onClick={() => void scan.resume()}>Wznów sprawdzanie zadania</button>}</div></div>}
                {scan.busy && !scan.job && <p className="inline-loading" role="status"><LoaderCircle className="spin" size={17}/>Przygotowujemy skan…</p>}
                {scan.job && <div className="scan-feedback"><ScanProgress job={scan.job} lost={scan.lost}/>
                    {scan.job.errorMessage && <div className="feedback feedback-error" role="alert">{scan.job.errorMessage}</div>}
                    <ScanSummary summary={scan.job.summary} completed={scan.job.status === 'COMPLETED'}/></div>}
                {scan.rejectionsError && <div className="feedback feedback-error" role="alert">{scan.rejectionsError}<button className="text-button" disabled={scan.requestActive} onClick={() => void scan.retryRejections()}>Ponów pobranie powodów</button></div>}
                {scan.rejections && scan.rejections.totalRejected > 0 && <details className="rejected-details"><summary>Nie wszystkie wpisy są obrazami · pominięto {scan.rejections.totalRejected}</summary>
                    {scan.rejections.truncated && <p>Przykłady: {scan.rejections.files.length} z {scan.rejections.totalRejected} pominiętych wpisów.</p>}
                    <ul>{scan.rejections.files.map(file => <li key={file.path}><strong>{file.originalName}</strong><span>{rejectionLabel(file.reason)}<details><summary>Kod diagnostyczny</summary><code>{file.reason}</code></details></span></li>)}</ul></details>}

                {/* Keep every view mounted: switching tabs must not throw away a review draft or a selection. */}
                <section hidden={view !== 'catalog'} id="catalog" aria-label="Biblioteka">
                    {catalogFolder ? <LibraryView library={library.data} loading={library.isPending} active={view === 'catalog'}
                        error={library.isError ? library.error.message : null} onRetry={() => void library.refetch()}
                        onScan={() => {setError(null); void scan.start(catalogFolder)}} scanDisabled={locked}
                        onReview={() => navigate('organization')} onDuplicates={() => navigate('duplicates')}/>
                        : <div className="catalog-empty"><FolderOpen size={36}/><h3>Wybierz folder na początek.</h3>
                            <p>Wybierz źródło i zeskanuj jego zawartość. Twoje obrazy zostają dokładnie tam, gdzie są.</p></div>}
                </section>
                <section hidden={view !== 'duplicates'} id="duplicates" aria-label="Duplikaty">
                    <DuplicatesView library={library.data} onBack={() => navigate('catalog')}/>
                </section>
                <section hidden={view !== 'organization'} id="organization" aria-label="Analiza i przegląd propozycji">
                    {organizationVisited && (catalogFolder ? <OrganizationPanel key={catalogFolder} folderPath={catalogFolder} fileCount={catalogCount.data ?? counts.total ?? 0} scanning={scan.busy} active={view === 'organization'} onEditingChange={setEditingReview} onExportingChange={setExporting}/>
                        : <div className="catalog-empty"><FolderOpen size={32}/><h3>Najpierw wybierz folder.</h3><p>Skan tworzy katalog, na którym pracuje analiza.</p><button className="secondary-button" onClick={() => setView('catalog')}>Wróć do katalogu</button></div>)}
                </section>
                <m.footer initial={{opacity: 0}} animate={{opacity: 1}} className="app-footer"><span><ShieldCheck size={14}/>Oryginały bez zmian. Decyzje należą do Ciebie.</span><span>ScreenSort · wersja rozwojowa</span></m.footer>
            </div>
        </main>
    </div>
}

export default App
