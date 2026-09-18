import {useState} from 'react'
import './App.css'

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
}

function formatSize(bytes: number): string {
    if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
    if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
    if (bytes >= 1024) return `${(bytes / 1024).toFixed(2)} KB`
    return `${bytes} B`
}

function App() {
    const [folderPath, setFolderPath] = useState('demo-images')
    const [result, setResult] = useState<ScanResult | null>(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

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
                        placeholder="demo-images"
                        aria-label="Folder path"
                        className="folder-input"
                    />
                    <button type="button" onClick={handleScan} disabled={loading}>
                        {loading ? 'Scanning…' : 'Scan folder'}
                    </button>
                </div>

                {error && <p className="scan-error">{error}</p>}
            </section>

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
        </main>
    )
}

export default App
