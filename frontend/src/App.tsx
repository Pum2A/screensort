import {useEffect, useState} from 'react'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {ScanSummary} from './scanning/ScanSummary'
import type {ScanSummaryData} from './scanning/ScanSummary'
import {CatalogGallery} from './catalog/CatalogGallery'
import {fetchCatalog} from './catalog/api'
import {folderName} from './catalog/format'
import type {CatalogPage} from './catalog/types'
import {Icon} from './ui/Icon'
import './App.css'

const LAST_FOLDER_KEY = 'screensort.lastFolder'
const CATALOG_PAGE_SIZE = 50

type ScanResult = {
    accepted: {path: string, originalName: string, extension: string, sizeBytes: number}[]
    rejected: {path: string, originalName: string, reason: string}[]
    summary: ScanSummaryData
}

function App() {
    const desktop = isTauri()
    const [folderPath, setFolderPath] = useState(() => localStorage.getItem(LAST_FOLDER_KEY) ?? 'demo-images')
    const [result, setResult] = useState<ScanResult | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [catalogFolder, setCatalogFolder] = useState(folderPath)
    const [catalogRefresh, setCatalogRefresh] = useState(0)
    const [catalogPageNumber, setCatalogPageNumber] = useState(0)
    const [catalogPage, setCatalogPage] = useState<CatalogPage | null>(null)
    const [catalogLoading, setCatalogLoading] = useState(true)
    const [catalogError, setCatalogError] = useState<string | null>(null)

    function showCatalog(folder: string) {
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
                setResult(null)
                showCatalog(selected)
            }
        } catch {
            setError('Nie udało się otworzyć okna wyboru folderu.')
        }
    }

    useEffect(() => {
        let ignore = false
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

    async function handleScan() {
        setLoading(true)
        setError(null)
        try {
            const response = await fetch('/api/scan', {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({folderPath}),
            })
            if (!response.ok) {
                setError(`Skanowanie nie powiodło się (HTTP ${response.status}).`)
                setResult(null)
                return
            }
            const data: ScanResult = await response.json()
            setResult(data)
            localStorage.setItem(LAST_FOLDER_KEY, folderPath)
            showCatalog(folderPath)
        } catch {
            setError('Nie udało się połączyć z backendem. Sprawdź, czy działa na porcie 8080.')
            setResult(null)
        } finally {
            setLoading(false)
        }
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
            <span className="local-badge"><Icon name="shield"/>Lokalnie. Prywatnie.</span>
        </header>

        <section className="workspace-heading" aria-labelledby="page-title">
            <div><p className="eyebrow">Twój mały porządek w dużym chaosie</p><h1 id="page-title">Twoje obrazy<span>.</span></h1>
                <p className="description">Wybierz folder, zeskanuj i przyjrzyj się temu, co już masz.</p></div>
            <span className="workspace-tag"><Icon name="grid"/>Katalog obrazów</span>
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
                    setResult(null)
                    showCatalog(folderPath)
                }} disabled={loading || catalogLoading}><Icon name="folder"/>Otwórz katalog</button>}
                <button type="button" className="primary-button" onClick={handleScan} disabled={loading}>
                    <Icon name={loading ? 'refresh' : 'scan'} className={loading ? 'spin' : ''}/>
                    {loading ? 'Skanowanie…' : 'Skanuj folder'}
                </button>
            </div>
        </section>
        {error && <p className="error-message" role="alert">{error}</p>}
        {loading && <p className="status-message" role="status">Sprawdzamy pliki i zapisujemy wyniki. Oryginały pozostają bez zmian.</p>}
        {result && <ScanSummary summary={result.summary}/>}
        {result && result.rejected.length > 0 && <details className="rejected-details">
            <summary>Dlaczego pominięto {result.rejected.length} plików?</summary>
            <ul>{result.rejected.map((file) => <li key={file.path}><strong>{file.originalName}</strong><code>{file.reason}</code></li>)}</ul>
        </details>}

        <section className="catalog" aria-labelledby="catalog-title">
            <div className="catalog-heading"><div><h2 id="catalog-title">{folderName(catalogFolder)}
                {catalogPage && <span className="count-badge">{catalogPage.totalFiles}</span>}</h2>
                <p className="catalog-folder" title={catalogFolder}>{catalogFolder}</p></div>
                <span className="catalog-hint">Kliknij obraz, żeby zobaczyć szczegóły</span>
            </div>
            {catalogLoading ? <div className="catalog-empty" role="status"><Icon name="refresh" className="spin"/>
                <h3>Ładujemy Twój katalog</h3><p>Pobieramy opisy zapisanych plików.</p></div>
                : catalogError ? <div className="catalog-empty"><Icon name="folder"/><h3>Katalog jest niedostępny</h3>
                    <p role="alert">{catalogError}</p><button type="button" className="secondary-button" onClick={() => showCatalog(catalogFolder)}>
                        <Icon name="refresh"/>Spróbuj ponownie</button></div>
                : !catalogPage || catalogPage.files.length === 0 ? <div className="catalog-empty"><Icon name="image"/>
                    <h3>{catalogPage?.totalFiles === 0 ? 'Tu zaczyna się porządek' : 'Ta strona jest pusta'}</h3>
                    <p>{catalogPage?.totalFiles === 0 ? 'Zeskanuj ten folder, a zapisane obrazy pojawią się tutaj.' : 'Wróć do poprzedniej strony katalogu.'}</p></div>
                : <CatalogGallery key={`${catalogFolder}:${catalogPageNumber}:${catalogRefresh}`} files={catalogPage.files}/>}
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
