import {useState} from 'react'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {CircleAlert, FolderOpen, ShieldCheck} from 'lucide-react'
import * as m from 'motion/react-m'
import {useScanJob} from './scanning/useScanJob.js'
import {ScanFeedback} from './scanning/ScanFeedback.js'
import {CatalogSection} from './catalog/CatalogSection.js'
import {useCatalogPage} from './catalog/useCatalogPage.js'
import {OrganizationPanel} from './organization/OrganizationPanel.js'
import {FolderToolbar} from './workspace/FolderToolbar.js'
import {WorkspaceNavigation} from './workspace/WorkspaceNavigation.js'
import {WorkspaceHeader} from './workspace/WorkspaceHeader.js'
import {storedFolder} from './workspace/state.js'
import type {WorkspaceView} from './workspace/state.js'
import './App.css'
import {useUpdateBlocker} from './desktop/updates/context.js'

// Wires the workspace together. Each part owns its own state: the catalog page lives in TanStack Query
// (useCatalogPage), the scan in useScanJob; App keeps only what the parts share.
function App({initialFolder}: {initialFolder?: string} = {}) {
    const desktop = isTauri()
    // The typed or chosen folder, which becomes the shown catalog only on "open" or after a scan.
    const [folderPath, setFolderPath] = useState(() => initialFolder ?? storedFolder())
    const catalog = useCatalogPage(folderPath)
    const [view, setView] = useState<WorkspaceView>('catalog')
    const [organizationVisited, setOrganizationVisited] = useState(false)
    const [error, setError] = useState<string | null>(null)
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
        if (folder !== catalog.folder) setOrganizationVisited(false)
        catalog.show(folder)
        setView('catalog')
    }

    function navigate(next: WorkspaceView) {
        if (next === 'organization') setOrganizationVisited(true)
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

    return <div className="app-shell">
        <WorkspaceNavigation total={catalog.page?.totalFiles ?? null} view={view} onView={navigate}/>
        <main className="workspace-main">
            <div className="workspace-content">
                <WorkspaceHeader view={view}/>
                <FolderToolbar desktop={desktop} folderPath={folderPath} locked={locked} loading={catalog.loading} scanning={scan.busy}
                    onFolderPath={setFolderPath} onChoose={() => void handleChooseFolder()}
                    onOpen={() => {scan.clear(); showCatalog(folderPath)}} onScan={() => {setError(null); void scan.start(folderPath)}}/>
                {editingReview && <div className="feedback feedback-warning" role="status">Masz niezapisane zmiany w przeglądzie propozycji. Zachowaliśmy je; przed zmianą folderu zapisz lub odrzuć edycję.</div>}
                {error && <div className="feedback feedback-error" role="alert"><CircleAlert size={18}/>{error}</div>}
                <ScanFeedback scan={scan}/>

                {/* Keep both views mounted: switching tabs must not throw away a review draft. */}
                <CatalogSection catalog={catalog} hidden={view !== 'catalog'} scanning={scan.busy} onOrganize={() => navigate('organization')}/>
                <section hidden={view !== 'organization'} id="organization" aria-label="Analiza i przegląd propozycji">
                    {organizationVisited && (catalog.folder ? <OrganizationPanel key={catalog.folder} folderPath={catalog.folder} fileCount={catalog.page?.totalFiles ?? 0} scanning={scan.busy} onEditingChange={setEditingReview} onExportingChange={setExporting}/>
                        : <div className="catalog-empty"><FolderOpen size={32}/><h3>Najpierw wybierz folder.</h3><p>Skan tworzy katalog, na którym pracuje analiza.</p><button className="secondary-button" onClick={() => setView('catalog')}>Wróć do katalogu</button></div>)}
                </section>
                <m.footer initial={{opacity: 0}} animate={{opacity: 1}} className="app-footer"><span><ShieldCheck size={14}/>Oryginały bez zmian. Decyzje należą do Ciebie.</span><span>ScreenSort · wersja rozwojowa</span></m.footer>
            </div>
        </main>
    </div>
}

export default App
