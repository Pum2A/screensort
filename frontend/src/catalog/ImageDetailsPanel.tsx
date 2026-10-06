import {useEffect, useRef} from 'react'
import {CatalogImage} from './CatalogImage'
import {imageUrl} from './api'
import {formatDate, formatSize} from './format'
import type {CatalogFile} from './types'
import {Icon} from '../ui/Icon'

export function ImageDetailsPanel({file, onClose}: {file: CatalogFile, onClose: () => void}) {
    const panel = useRef<HTMLElement>(null)
    useEffect(() => {
        panel.current?.focus({preventScroll: true})
        if (window.matchMedia('(max-width: 760px)').matches) {
            panel.current?.scrollIntoView({block: 'start', behavior: 'smooth'})
        }
    }, [file.id])
    const src = imageUrl(file, 'preview')
    return <aside className="image-details" aria-label={`Szczegóły: ${file.originalName}`} tabIndex={-1} ref={panel}>
        <div className="details-heading"><span className="eyebrow">Podgląd obrazu</span>
            <button type="button" className="icon-button" aria-label="Zamknij szczegóły" onClick={onClose}><Icon name="close"/></button>
        </div>
        <CatalogImage key={src} src={src} name={file.originalName} preview/>
        <h3>{file.originalName}</h3>
        <p className="file-subtitle"><span className="format-badge">{file.extension.toUpperCase()}</span>{formatSize(file.sizeBytes)}</p>
        <dl className="file-properties">
            <div><dt>Zmodyfikowany</dt><dd>{formatDate(file.lastModifiedAt)}</dd></div>
            <div><dt>Pierwsze skanowanie</dt><dd>{formatDate(file.firstDiscoveredAt)}</dd></div>
            <div><dt>Ostatnio widziany</dt><dd>{formatDate(file.lastSeenAt)}</dd></div>
            <div><dt>Folder źródłowy</dt><dd>{file.sourceFolder}</dd></div>
            <div><dt>Ścieżka w folderze</dt><dd>{file.relativePath}</dd></div>
        </dl>
        <details className="technical-details"><summary>Dane techniczne</summary>
            <dl className="file-properties">
                <div><dt>ID pliku / folderu</dt><dd>{file.id} / {file.allowedRootId}</dd></div>
                <div><dt>Dokładny rozmiar</dt><dd>{file.sizeBytes.toLocaleString('pl-PL')} B</dd></div>
                <div><dt>SHA-256 z ostatniego sprawdzenia</dt><dd><code>{file.sha256}</code></dd></div>
                <div><dt>Data modyfikacji (UTC)</dt><dd><code>{file.lastModifiedAt}</code></dd></div>
                <div><dt>Pierwsze skanowanie (UTC)</dt><dd><code>{file.firstDiscoveredAt}</code></dd></div>
                <div><dt>Ostatnio widziany (UTC)</dt><dd><code>{file.lastSeenAt}</code></dd></div>
            </dl>
        </details>
        <p className="read-only-note"><Icon name="shield"/>Tylko podgląd. Nie zmieniamy plików.</p>
        <p className="metadata-note">Opis z ostatniego skanu; podgląd z pliku na dysku.</p>
    </aside>
}
