import {useEffect, useState} from 'react'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {ScanSummary} from './scanning/ScanSummary'
import {ScanProgress} from './scanning/ScanProgress'
import {useScanJob} from './scanning/useScanJob'
import {CatalogGallery} from './catalog/CatalogGallery'
import {fetchCatalog} from './catalog/api'
import {folderName} from './catalog/format'
import type {CatalogPage} from './catalog/types'
import {Icon} from './ui/Icon'
import './App.css'

const LAST_FOLDER_KEY = 'screensort.lastFolder'
const CATALOG_PAGE_SIZE = 50

function App() {
    const desktop = isTauri()
    const [folderPath, setFolderPath] = useState(() => localStorage.getItem(LAST_FOLDER_KEY) ?? (desktop ? '' : 'demo-images'))
    const [error, setError] = useState<string | null>(null)
    const [catalogFolder, setCatalogFolder] = useState(folderPath)
    const [catalogRefresh, setCatalogRefresh] = useState(0)
    const [catalogPageNumber, setCatalogPageNumber] = useState(0)
    const [catalogPage, setCatalogPage] = useState<CatalogPage | null>(null)
    const [catalogLoading, setCatalogLoading] = useState(Boolean(folderPath.trim()))
    const [catalogError, setCatalogError] = useState<string | null>(null)
    const [analysisDemoOpen, setAnalysisDemoOpen] = useState(false)
    const scan = useScanJob((completed) => {
        try { localStorage.setItem(LAST_FOLDER_KEY, completed.folderPath) }
        catch { /* Katalog działa również, gdy przeglądarka blokuje zapis preferencji. */ }
        showCatalog(completed.folderPath)
    })
    const loading = scan.busy

    function showCatalog(folder: string) {
        setAnalysisDemoOpen(false)
        setCatalogPage(null)
        setCatalogPageNumber(0)
        setCatalogLoading(true)
        setCatalogError(null)
        setCatalogFolder(folder)
        setCatalogRefresh((current) => current + 1)
    }

    async function handleChooseFolder() {
        setError(null)
        try {
            const selected = await open({directory: true, multiple: false})
            if (typeof selected === 'string') {
                setFolderPath(selected)
                scan.clear()
                showCatalog(selected)
            }
        } catch {
            setError('Nie udało się otworzyć okna wyboru folderu.')
        }
    }

    useEffect(() => {
        let ignore = false
        if (!catalogFolder.trim()) {
            return
        }
        fetchCatalog(catalogFolder, catalogPageNumber, CATALOG_PAGE_SIZE)
            .then((page) => { if (!ignore) setCatalogPage(page) })
            .catch(() => {
                if (!ignore) {
                    setCatalogPage(null)
                    setCatalogError('Nie udało się pobrać katalogu. Sprawdź folder i działanie backendu.')
                }
            })
            .finally(() => { if (!ignore) setCatalogLoading(false) })
        return () => { ignore = true }
    }, [catalogFolder, catalogPageNumber, catalogRefresh])

    function handleScan() {
        setError(null)
        void scan.start(folderPath)
    }

    function changePage(direction: -1 | 1) {
        setCatalogLoading(true)
        setCatalogError(null)
        setCatalogPageNumber((current) => Math.max(0, current + direction))
    }

    const pageCount = catalogPage ? Math.ceil(catalogPage.totalFiles / catalogPage.pageSize) : 0
    const rangeStart = catalogPage && catalogPage.files.length > 0 ? catalogPage.pageNumber * catalogPage.pageSize + 1 : 0
    const rangeEnd = catalogPage ? rangeStart + Math.max(0, catalogPage.files.length - 1) : 0

    return <main className="app-shell">
        <header className="topbar">
            <a className="brand" href="/" aria-label="ScreenSort — strona główna">
                <span className="brand-mark" aria-hidden="true"><Icon name="scan"/></span>ScreenSort
            </a>
            <span className="local-badge"><Icon name="shield"/>LOCAL / READ ONLY</span>
        </header>

        <section className="workspace-heading" aria-labelledby="page-title">
            <h1 id="page-title">Eksplorator obrazów</h1>
            <p>Wybierz folder → skanuj → odkrywaj pliki</p>
        </section>

        <section className="folder-toolbar" aria-label="Wybór i skanowanie folderu">
            <div className="folder-field"><span className="folder-symbol"><Icon name="folder"/></span>
                <label htmlFor="folder-path">Folder źródłowy
                    <input id="folder-path" type="text" value={folderPath} onChange={(event) => setFolderPath(event.target.value)}
                        readOnly={desktop} disabled={loading} placeholder="demo-images" title={folderPath}/>
                </label>
            </div>
            <div className="folder-actions">
                {desktop ? <button type="button" className="secondary-button" onClick={handleChooseFolder} disabled={loading}>
                    <Icon name="folder"/>Wybierz folder
                </button> : <button type="button" className="secondary-button" onClick={() => {
                    scan.clear()
                    showCatalog(folderPath)
                }} disabled={loading || catalogLoading}><Icon name="folder"/>Otwórz katalog</button>}
                <button type="button" className="primary-button" onClick={handleScan} disabled={loading || !folderPath.trim()}>
                    <Icon name={loading ? 'refresh' : 'scan'} className={loading ? 'spin' : ''}/>
                    {loading ? 'Skanowanie…' : 'Skanuj folder'}
                </button>
            </div>
        </section>
        {error && <p className="error-message" role="alert">{error}</p>}
        {scan.error && <div className="error-message" role="alert"><p>{scan.error}</p>
            {scan.job && !scan.lost && scan.job.status !== 'COMPLETED' && scan.job.status !== 'FAILED'
                && <button type="button" className="secondary-button" disabled={scan.requestActive} onClick={() => void scan.resume()}>
                    Wznów sprawdzanie zadania</button>}</div>}
        {loading && !scan.job && <p className="status-message" role="status">Zlecamy skan…</p>}
        {scan.job && <>
            <ScanProgress job={scan.job} lost={scan.lost}/>
            {scan.job.errorMessage && <p className="error-message" role="alert">{scan.job.errorMessage}</p>}
            <ScanSummary summary={scan.job.summary} completed={scan.job.status === 'COMPLETED'}/>
        </>}
        {scan.rejectionsError && <p className="error-message" role="alert">{scan.rejectionsError}{' '}
            <button type="button" className="secondary-button" disabled={scan.requestActive} onClick={() => void scan.retryRejections()}>
                Ponów pobranie powodów</button></p>}
        {scan.rejections && scan.rejections.totalRejected > 0 && <details className="rejected-details">
            <summary>Powody pominięcia wpisów ({scan.rejections.totalRejected})</summary>
            {scan.rejections.truncated && <p>Pokazujemy {scan.rejections.files.length} przykładów z {scan.rejections.totalRejected} odrzuconych wpisów.</p>}
            <ul>{scan.rejections.files.map((file) => <li key={file.path}><strong>{file.originalName}</strong><code>{file.reason}</code></li>)}</ul>
        </details>}

        <section className="catalog" aria-labelledby="catalog-title">
            <div className="catalog-heading"><div><h2 id="catalog-title">{folderName(catalogFolder)}
                {catalogPage && <span className="count-badge">{catalogPage.totalFiles}</span>}</h2>
                <p className="catalog-folder" title={catalogFolder}>{catalogFolder}</p></div>
                <div className="catalog-actions"><span className="catalog-hint">Kliknij kółko → szczegóły obok</span>
                    <button type="button" className="secondary-button" aria-pressed={analysisDemoOpen}
                        onClick={() => setAnalysisDemoOpen(current => !current)}>
                        <Icon name="eye"/>Analiza <span className="demo-badge">DEMO</span>
                    </button></div>
            </div>
            <CatalogGallery key={`${catalogFolder}:${catalogPageNumber}:${catalogRefresh}`}
                files={catalogLoading || catalogError ? [] : catalogPage?.files ?? []}
                demoOpen={analysisDemoOpen} onCloseDemo={() => setAnalysisDemoOpen(false)}>
            {catalogLoading ? <div className="catalog-empty" role="status"><Icon name="refresh" className="spin"/>
                <h3>Ładujemy Twój katalog</h3><p>Pobieramy opisy zapisanych plików.</p></div>
                : catalogError ? <div className="catalog-empty"><Icon name="folder"/><h3>Katalog jest niedostępny</h3>
                    <p role="alert">{catalogError}</p><button type="button" className="secondary-button" onClick={() => showCatalog(catalogFolder)}>
                        <Icon name="refresh"/>Spróbuj ponownie</button></div>
                : !catalogPage || catalogPage.files.length === 0 ? <div className="catalog-empty"><Icon name="image"/>
                    <h3>{catalogPage?.totalFiles === 0 ? 'Brak zapisanych obrazów' : 'Ta strona jest pusta'}</h3>
                    <p>{catalogPage?.totalFiles === 0 ? 'Zeskanuj ten folder, a zapisane obrazy pojawią się tutaj.' : 'Wróć do poprzedniej strony katalogu.'}</p></div>
                : undefined}
            </CatalogGallery>
            {catalogPage && catalogPage.totalFiles > 0 && <nav className="catalog-pagination" aria-label="Strony katalogu">
                <span className="page-range">{rangeStart}–{rangeEnd} z {catalogPage.totalFiles} obrazów</span>
                <div className="page-controls"><button type="button" className="icon-button" aria-label="Poprzednia strona"
                    disabled={catalogLoading || loading || catalogPageNumber === 0} onClick={() => changePage(-1)}><Icon name="left"/></button>
                    <span aria-live="polite">{catalogPage.pageNumber + 1} / {pageCount}</span>
                    <button type="button" className="icon-button" aria-label="Następna strona"
                        disabled={catalogLoading || loading || !catalogPage.hasNext} onClick={() => changePage(1)}><Icon name="right"/></button></div>
            </nav>}
        </section>
        <footer className="app-footer"><span><Icon name="shield"/>Twoje pliki zostają na Twoim komputerze.</span><span>ScreenSort · podgląd tylko do odczytu</span></footer>
    </main>
}

export default App
