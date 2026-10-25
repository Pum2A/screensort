import {CatalogImage} from './CatalogImage.js'
import {imageUrl} from './api.js'
import {formatDate, formatSize} from './format.js'
import type {CatalogFile} from './types.js'
import {Icon} from '../ui/Icon.js'
import {useTreeConnections} from './useTreeConnections.js'
import {AnimatedBranch} from '../ui/AnimatedBranch.js'
import {AnalysisPanel} from '../analysis/AnalysisPanel.js'
import './ImageDetailsTree.css'

export function ImageDetailsTree({file}: {file: CatalogFile}) {
    const {tree, connections} = useTreeConnections(file.id)
    const src = imageUrl(file, 'preview')
    return <section className="image-details-tree" aria-label={`Szczegóły: ${file.originalName}`} ref={tree}>
        {connections && <svg className="tree-connections" width={connections.width} height={connections.height}
            viewBox={`0 0 ${connections.width} ${connections.height}`} aria-hidden="true">
            {connections.paths.map((path, index) => <path key={index} d={path}/>)}
        </svg>}
        <div className="tree-preview" data-tree-preview>
            <div className="details-heading"><span>WYBRANY OBRAZ</span></div>
            <CatalogImage key={src} src={src} name={file.originalName} preview/>
            <h3>{file.originalName}</h3>
            <p className="read-only-note"><Icon name="shield"/>Podgląd. Oryginał bez zmian.</p>
            <p className="metadata-note">Opis z ostatniego skanu. Podgląd z dysku.</p>
        </div>
        <div className="tree-branches">
            {/* The single-image analysis and name editor, formerly in the library side panel. */}
            <div className="tree-branch" data-tree-branch><AnimatedBranch label="Analiza obrazu" defaultOpen>
                <AnalysisPanel key={file.id} fileId={file.id}/>
            </AnimatedBranch></div>
            <div className="tree-branch" data-tree-branch><AnimatedBranch label="Plik" defaultOpen>
                <dl className="file-properties">
                    <div><dt>Nazwa</dt><dd>{file.originalName}</dd></div>
                    <div><dt>Format / rozmiar</dt><dd>{file.extension.toUpperCase()} · {formatSize(file.sizeBytes)}</dd></div>
                </dl>
            </AnimatedBranch></div>
            <div className="tree-branch" data-tree-branch><AnimatedBranch label="Lokalizacja" defaultOpen>
                <dl className="file-properties">
                    <div><dt>Folder źródłowy</dt><dd>{file.sourceFolder}</dd></div>
                    <div><dt>Ścieżka w folderze</dt><dd>{file.relativePath}</dd></div>
                </dl>
            </AnimatedBranch></div>
            <div className="tree-branch" data-tree-branch><AnimatedBranch label="Historia" defaultOpen>
                <dl className="file-properties">
                    <div><dt>Zmodyfikowany</dt><dd>{formatDate(file.lastModifiedAt)}</dd></div>
                    <div><dt>Pierwsze skanowanie</dt><dd>{formatDate(file.firstDiscoveredAt)}</dd></div>
                    <div><dt>Ostatnio widziany</dt><dd>{formatDate(file.lastSeenAt)}</dd></div>
                </dl>
            </AnimatedBranch></div>
            <div className="tree-branch" data-tree-branch><AnimatedBranch label="Techniczne">
            <dl className="file-properties">
                <div><dt>ID pliku / folderu</dt><dd>{file.id} / {file.allowedRootId}</dd></div>
                <div><dt>Dokładny rozmiar</dt><dd>{file.sizeBytes.toLocaleString('pl-PL')} B</dd></div>
                <div><dt>SHA-256 z ostatniego sprawdzenia</dt><dd><code>{file.sha256}</code></dd></div>
                <div><dt>Data modyfikacji (UTC)</dt><dd><code>{file.lastModifiedAt}</code></dd></div>
                <div><dt>Pierwsze skanowanie (UTC)</dt><dd><code>{file.firstDiscoveredAt}</code></dd></div>
                <div><dt>Ostatnio widziany (UTC)</dt><dd><code>{file.lastSeenAt}</code></dd></div>
            </dl>
            </AnimatedBranch></div>
        </div>
    </section>
}
