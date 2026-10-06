import {useEffect, useState} from 'react'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {ScanSummary} from './scanning/ScanSummary'
import type {ScanSummaryData} from './scanning/ScanSummary'
import './App.css'

const LAST_FOLDER_KEY = 'screensort.lastFolder'

type AcceptedImage = {
    path: string
    originalName: string
    extension: string
    sizeBytes: number
}

type RejectedFile = {
    path: string
    originalName: string
    reason: string
}

type ScanResult = {
    accepted: AcceptedImage[]
    rejected: RejectedFile[]
    summary: ScanSummaryData
}

type CatalogFile = {
    id: number
    allowedRootId: number
    sourceFolder: string
    relativePath: string
    originalName: string
    extension: string
    sizeBytes: number
    lastModifiedAt: string
    sha256: string
    firstDiscoveredAt: string
    lastSeenAt: string
}

async function fetchCatalog(folderPath: string): Promise<CatalogFile[]> {
    const query = new URLSearchParams({folderPath})
    const response = await fetch(`/api/catalog/files?${query}`)

    if (!response.ok) {
        throw new Error(`Catalog request failed (HTTP ${response.status})`)
    }

    return await response.json() as CatalogFile[]
}

function formatSize(bytes: number): string {
    if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
    if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
    if (bytes >= 1024) return `${(bytes / 1024).toFixed(2)} KB`
    return `${bytes} B`
}

function App() {
    const desktop = isTauri()
    const [folderPath, setFolderPath] = useState(
        () => localStorage.getItem(LAST_FOLDER_KEY) ?? 'demo-images',
    )
    const [result, setResult] = useState<ScanResult | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [catalogFolder, setCatalogFolder] = useState(folderPath)
    const [catalogRefresh, setCatalogRefresh] = useState(0)
    const [catalogFiles, setCatalogFiles] = useState<CatalogFile[] | null>(null)
    const [catalogLoading, setCatalogLoading] = useState(true)
    const [catalogError, setCatalogError] = useState<string | null>(null)

    async function handleChooseFolder() {
        setError(null)

        try {
            const selected = await open({directory: true, multiple: false})
            if (typeof selected === 'string') {
                setFolderPath(selected)
                setResult(null)
            }
        } catch {
            setError('Nie udało się otworzyć okna wyboru folderu.')
        }
    }

    useEffect(() => {
        let ignore = false

        fetchCatalog(catalogFolder)
            .then((files) => {
                if (!ignore) setCatalogFiles(files)
            })
            .catch(() => {
                if (!ignore) {
                    setCatalogFiles(null)
                    setCatalogError('Could not load saved images from the catalog.')
                }
            })
            .finally(() => {
                if (!ignore) setCatalogLoading(false)
            })

        return () => {
            ignore = true
        }
    }, [catalogFolder, catalogRefresh])

    async function handleScan() {
        setLoading(true)
        setError(null)

        try {
            const response = await fetch('/api/scan', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
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
            setCatalogFiles(null)
            setCatalogLoading(true)
            setCatalogError(null)
            setCatalogFolder(folderPath)
            setCatalogRefresh((current) => current + 1)
        } catch {
            setError('Nie udało się połączyć z backendem. Sprawdź, czy działa na porcie 8080.')
            setResult(null)
        } finally {
            setLoading(false)
        }
    }

    return (
        <main className="app-shell">
            <header className="topbar">
                <a className="brand" href="/" aria-label="ScreenSort home">
                    <span className="brand-mark" aria-hidden="true">S</span>
                    <span>ScreenSort</span>
                </a>
                <span className="local-badge">Local only</span>
            </header>

            <section className="hero" aria-labelledby="page-title">
                <p className="eyebrow">Private image workspace</p>
                <h1 id="page-title">Scan a folder of screenshots.</h1>
                <p className="description">
                    ScreenSort scans only the folder you choose. Source images stay
                    read-only while the app prepares safe metadata for review.
                </p>

                <div className="action-row">
                    <input
                        type="text"
                        value={folderPath}
                        onChange={(event) => setFolderPath(event.target.value)}
                        readOnly={desktop}
                        placeholder="demo-images"
                        aria-label="Folder path"
                        className="folder-input"
                    />
                    {desktop && (
                        <button type="button" className="secondary-button" onClick={handleChooseFolder}
                                disabled={loading}>
                            Wybierz folder
                        </button>
                    )}
                    <button type="button" onClick={handleScan} disabled={loading}>
                        {loading ? 'Scanning…' : 'Scan folder'}
                    </button>
                </div>

                {error && <p className="scan-error">{error}</p>}
            </section>

            {result && <ScanSummary summary={result.summary}/>}

            {result && (
                <section className="results" aria-label="Scan results">
                    <div className="result-block">
                        <h2>Accepted images ({result.accepted.length})</h2>
                        {result.accepted.length === 0 ? (
                            <p className="empty">No supported images found.</p>
                        ) : (
                            <ul>
                                {result.accepted.map((image) => (
                                    <li key={image.path}>
                                        <strong>{image.originalName}</strong>
                                        <span>{image.extension.toUpperCase()}</span>
                                        <span>{formatSize(image.sizeBytes)}</span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>

                    <div className="result-block">
                        <h2>Rejected files ({result.rejected.length})</h2>
                        {result.rejected.length === 0 ? (
                            <p className="empty">Nothing was rejected.</p>
                        ) : (
                            <ul>
                                {result.rejected.map((file) => (
                                    <li key={file.path}>
                                        <strong>{file.originalName}</strong>
                                        <span className="reason">{file.reason}</span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                </section>
            )}

            <section className="catalog" aria-label="Saved images">
                <h2>Saved images {catalogFiles && `(${catalogFiles.length})`}</h2>
                <p className="catalog-folder">Folder: <code>{catalogFolder}</code></p>

                {catalogLoading ? (
                    <p className="empty">Loading saved images...</p>
                ) : catalogError ? (
                    <p className="scan-error">{catalogError}</p>
                ) : catalogFiles?.length === 0 ? (
                    <p className="empty">No saved images for this folder yet.</p>
                ) : (
                    <div className="catalog-list">
                        {(catalogFiles ?? []).map((file) => (
                            <details className="catalog-file" key={file.id}>
                                <summary>
                                    <strong>{file.originalName}</strong>
                                    <span>{file.extension.toUpperCase()}</span>
                                    <span>{formatSize(file.sizeBytes)}</span>
                                </summary>
                                <dl>
                                    <dt>Database ID</dt>
                                    <dd>{file.id}</dd>
                                    <dt>Folder ID</dt>
                                    <dd>{file.allowedRootId}</dd>
                                    <dt>Source folder</dt>
                                    <dd>{file.sourceFolder}</dd>
                                    <dt>Relative path</dt>
                                    <dd>{file.relativePath}</dd>
                                    <dt>Original name</dt>
                                    <dd>{file.originalName}</dd>
                                    <dt>Extension</dt>
                                    <dd>{file.extension}</dd>
                                    <dt>Size in bytes</dt>
                                    <dd>{file.sizeBytes}</dd>
                                    <dt>Last modified</dt>
                                    <dd>{file.lastModifiedAt}</dd>
                                    <dt>SHA-256</dt>
                                    <dd>{file.sha256}</dd>
                                    <dt>First discovered</dt>
                                    <dd>{file.firstDiscoveredAt}</dd>
                                    <dt>Last seen</dt>
                                    <dd>{file.lastSeenAt}</dd>
                                </dl>
                            </details>
                        ))}
                    </div>
                )}
            </section>
        </main>
    )
}

export default App
