import {CatalogImage} from './CatalogImage'
import {imageUrl} from './api'
import {formatDate, formatSize} from './format'
import type {CatalogFile} from './types'
import {AnimatedBranch} from '../ui/AnimatedBranch'
import {Icon} from '../ui/Icon'
import styles from './CatalogDetailsPanel.module.css'

export function CatalogDetailsPanel({file, onPreview}: {file: CatalogFile, onPreview: (trigger: HTMLButtonElement) => void}) {
    const src = imageUrl(file, 'thumbnail')
    return <aside className={styles.panel} aria-label={`Szczegóły: ${file.originalName}`}>
        <header className={styles.heading}><h3>Wybrany obraz</h3><span>Dane z katalogu</span></header>
        <div className={styles.body}>
            <div className={styles.selected}>
                <div className={styles.thumbnail}><CatalogImage key={src} src={src} name=""/></div>
                <div className={styles.description}><p className={styles.name}>{file.originalName}</p>
                    <p className={styles.muted}>{file.extension.toUpperCase()} · {formatSize(file.sizeBytes)}</p>
                    <button type="button" className="text-button" onClick={event => onPreview(event.currentTarget)}><Icon name="zoom"/>Powiększ obraz</button>
                </div>
            </div>
            <div className={styles.tree}>
                <AnimatedBranch label="Plik" icon={<Icon name="image"/>} defaultOpen>
                    <dl className={styles.properties}><div><dt>Oryginalna nazwa</dt><dd>{file.originalName}</dd></div>
                        <div><dt>Format / rozmiar</dt><dd>{file.extension.toUpperCase()} · {formatSize(file.sizeBytes)}</dd></div></dl>
                </AnimatedBranch>
                <AnimatedBranch label="Lokalizacja" icon={<Icon name="folder"/>}>
                    <dl className={styles.properties}><div><dt>Folder źródłowy</dt><dd>{file.sourceFolder}</dd></div>
                        <div><dt>Ścieżka w folderze</dt><dd>{file.relativePath}</dd></div></dl>
                </AnimatedBranch>
                <AnimatedBranch label="Historia" icon={<Icon name="history"/>}>
                    <dl className={styles.properties}><div><dt>Zmodyfikowany</dt><dd>{formatDate(file.lastModifiedAt)}</dd></div>
                        <div><dt>Pierwszy skan</dt><dd>{formatDate(file.firstDiscoveredAt)}</dd></div>
                        <div><dt>Ostatnio widziany</dt><dd>{formatDate(file.lastSeenAt)}</dd></div></dl>
                </AnimatedBranch>
                <AnimatedBranch label="Techniczne" icon={<Icon name="scan"/>}>
                    <dl className={styles.properties}><div><dt>ID pliku / folderu</dt><dd>{file.id} / {file.allowedRootId}</dd></div>
                        <div><dt>Dokładny rozmiar</dt><dd>{file.sizeBytes.toLocaleString('pl-PL')} B</dd></div>
                        <div><dt>SHA-256 z ostatniego sprawdzenia</dt><dd><code>{file.sha256}</code></dd></div>
                        <div><dt>Data modyfikacji (UTC)</dt><dd><code>{file.lastModifiedAt}</code></dd></div>
                        <div><dt>Pierwszy skan (UTC)</dt><dd><code>{file.firstDiscoveredAt}</code></dd></div>
                        <div><dt>Ostatnio widziany (UTC)</dt><dd><code>{file.lastSeenAt}</code></dd></div></dl>
                </AnimatedBranch>
            </div>
            <p className={styles.note}><Icon name="shield"/>Metadane z ostatniego skanu. Podgląd z dysku. Oryginał bez zmian.</p>
        </div>
    </aside>
}
