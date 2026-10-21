import {useEffect, useState} from 'react'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {ArrowRight, CircleAlert, FolderOpen, Images, LoaderCircle, ScanLine, ShieldCheck} from 'lucide-react'
import * as m from 'motion/react-m'
import {ScanSummary} from './scanning/ScanSummary'
import {ScanProgress} from './scanning/ScanProgress'
import {useScanJob} from './scanning/useScanJob'
import {CatalogGallery} from './catalog/CatalogGallery'
import {fetchCatalog} from './catalog/api'
import {folderName} from './catalog/format'
import type {CatalogPage} from './catalog/types'
import {Icon} from './ui/Icon'
import {OrganizationPanel} from './organization/OrganizationPanel'
import {rejectionLabel} from './scanning/rejectionLabels'
import {ExplorerSidebar} from './workspace/ExplorerSidebar'
import {WorkspaceHeader} from './workspace/WorkspaceHeader'
import {storedFolder} from './workspace/state'
import type {WorkspaceView} from './workspace/state'
import './App.css'
import {useUpdateBlocker} from './desktop/updates/context'

const CATALOG_PAGE_SIZE = 24

function App({initialFolder}: {initialFolder?: string} = {}) {
    const desktop = isTauri()
    const [folderPath, setFolderPath] = useState(() => initialFolder ?? storedFolder(desktop))
    const [view, setView] = useState<WorkspaceView>('catalog')
    const [organizationVisited, setOrganizationVisited] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [catalogFolder, setCatalogFolder] = useState(folderPath)
    const [catalogRefresh, setCatalogRefresh] = useState(0)
    const [catalogPageNumber, setCatalogPageNumber] = useState(0)
    const [catalogPage, setCatalogPage] = useState<CatalogPage | null>(null)
    const [catalogLoading, setCatalogLoading] = useState(Boolean(folderPath.trim()))
    const [catalogError, setCatalogError] = useState<string | null>(null)
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
        setCatalogPage(null); setCatalogPageNumber(0); setCatalogLoading(Boolean(folder.trim())); setCatalogError(null)
        setCatalogFolder(folder); setCatalogRefresh(current => current + 1); setView('catalog')
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

    useEffect(() => {
        let ignore = false
        if (!catalogFolder.trim()) return
        fetchCatalog(catalogFolder, catalogPageNumber, CATALOG_PAGE_SIZE)
            .then(page => {if (!ignore) setCatalogPage(page)})
            .catch(() => {if (!ignore) {setCatalogPage(null); setCatalogError('Nie udało się pobrać katalogu. Sprawdź folder i działanie backendu.')}})
            .finally(() => {if (!ignore) setCatalogLoading(false)})
        return () => {ignore = true}
    }, [catalogFolder, catalogPageNumber, catalogRefresh])

    function changePage(direction: -1 | 1) {
        setCatalogLoading(true); setCatalogError(null); setCatalogPageNumber(current => Math.max(0, current + direction))
    }

    const pageCount = catalogPage ? Math.ceil(catalogPage.totalFiles / catalogPage.pageSize) : 0
    const rangeStart = catalogPage && catalogPage.files.length > 0 ? catalogPage.pageNumber * catalogPage.pageSize + 1 : 0
    const rangeEnd = catalogPage && rangeStart > 0 ? rangeStart + catalogPage.files.length - 1 : 0

    return <div className="app-shell">
        <ExplorerSidebar folder={catalogFolder} total={catalogPage?.totalFiles ?? null} view={view} onView={navigate}
            onChoose={() => void handleChooseFolder()} desktop={desktop} locked={locked}/>
        <main className="workspace-main">
            <div className="workspace-content">
                <WorkspaceHeader view={view} folder={catalogFolder} total={catalogPage?.totalFiles ?? null} onAnalyze={() => navigate('organization')}/>
                <section className="folder-toolbar" aria-label="Wybór i skanowanie folderu">
                    <div className="folder-field"><span className="folder-symbol"><FolderOpen size={24}/></span><label htmlFor="folder-path">Folder źródłowy
                        <input id="folder-path" type="text" value={folderPath} onChange={event => setFolderPath(event.target.value)} readOnly={desktop} disabled={locked}
                            placeholder={desktop ? 'Wybierz folder z obrazami' : 'Wpisz ścieżkę folderu'} title={folderPath}/>
                    </label></div>
                    <div className="folder-actions">{desktop
                        ? <button className="secondary-button" disabled={locked} onClick={() => void handleChooseFolder()}><FolderOpen size={17}/>Wybierz folder</button>
                        : <button className="secondary-button" disabled={locked || catalogLoading || !folderPath.trim()} onClick={() => {scan.clear(); showCatalog(folderPath)}}><FolderOpen size={17}/>Otwórz katalog</button>}
                        <button className="primary-button" disabled={locked || !folderPath.trim()} onClick={() => {setError(null); void scan.start(folderPath)}}>
                            {scan.busy ? <LoaderCircle size={17} className="spin"/> : <ScanLine size={17}/>}{scan.busy ? 'Skanujemy…' : 'Skanuj folder'}</button>
                    </div>
                </section>
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

                {/* Keep both views mounted: switching tabs must not throw away a review draft. */}
                <section hidden={view !== 'catalog'} id="catalog" aria-labelledby="catalog-title" className="catalog">
                    <div className="catalog-heading"><div><span className="eyebrow">ZAPISANY KATALOG</span><h2 id="catalog-title"><FolderOpen size={21}/>{catalogFolder ? folderName(catalogFolder) : 'Twój pierwszy folder'}
                        {catalogPage && <span className="count-badge">{catalogPage.totalFiles}</span>}</h2></div>
                        <button className="text-button" onClick={() => navigate('organization')}>Analiza i propozycje<ArrowRight size={16}/></button></div>
                    <CatalogGallery key={[catalogFolder, catalogPageNumber, catalogRefresh].join(':')} files={catalogLoading || catalogError ? [] : catalogPage?.files ?? []}>
                        {catalogLoading ? <div className="catalog-skeleton" role="status"><span className="sr-only">Ładujemy katalog obrazów</span>{Array.from({length: 8}, (_, index) => <div className="skeleton-tile" key={index}><span/><i/><i/></div>)}</div>
                            : catalogError ? <div className="catalog-empty catalog-empty-error"><CircleAlert size={30}/><h3>Katalog jest niedostępny</h3><p role="alert">{catalogError}</p>
                                <button className="secondary-button" onClick={() => showCatalog(catalogFolder)}><Icon name="refresh"/>Spróbuj ponownie</button></div>
                            : !catalogPage || catalogPage.files.length === 0 ? <div className="catalog-empty"><Images size={36}/><h3>{catalogPage?.totalFiles === 0 ? 'Tu zaczyna się porządek.' : 'Wybierz folder na początek.'}</h3>
                                <p>Wybierz źródło i zeskanuj jego zawartość. Twoje obrazy zostają dokładnie tam, gdzie są.</p></div> : undefined}
                    </CatalogGallery>
                    {catalogPage && catalogPage.totalFiles > 0 && <nav className="catalog-pagination" aria-label="Strony katalogu"><span className="page-range">{rangeStart}–{rangeEnd} z {catalogPage.totalFiles} obrazów</span>
                        <div className="page-controls"><button className="icon-button" aria-label="Poprzednia strona" disabled={catalogLoading || scan.busy || catalogPageNumber === 0} onClick={() => changePage(-1)}><Icon name="left"/></button>
                            <span aria-live="polite">{catalogPage.pageNumber + 1} / {pageCount}</span><button className="icon-button" aria-label="Następna strona" disabled={catalogLoading || scan.busy || !catalogPage.hasNext} onClick={() => changePage(1)}><Icon name="right"/></button></div></nav>}
                </section>
                <section hidden={view !== 'organization'} id="organization" aria-label="Analiza i przegląd propozycji">
                    {organizationVisited && (catalogFolder ? <OrganizationPanel key={catalogFolder} folderPath={catalogFolder} fileCount={catalogPage?.totalFiles ?? 0} scanning={scan.busy} onEditingChange={setEditingReview} onExportingChange={setExporting}/>
                        : <div className="catalog-empty"><FolderOpen size={32}/><h3>Najpierw wybierz folder.</h3><p>Skan tworzy katalog, na którym pracuje analiza.</p><button className="secondary-button" onClick={() => setView('catalog')}>Wróć do katalogu</button></div>)}
                </section>
                <m.footer initial={{opacity: 0}} animate={{opacity: 1}} className="app-footer"><span><ShieldCheck size={14}/>Oryginały bez zmian. Decyzje należą do Ciebie.</span><span>ScreenSort · wersja rozwojowa</span></m.footer>
            </div>
        </main>
    </div>
}

export default App
