import {useMemo, useState} from 'react'
import {CatalogImage} from '../catalog/CatalogImage.js'
import {imageUrl} from '../catalog/api.js'
import {formatDate, formatSize} from '../catalog/format.js'
import {PixelIcon} from '../ui/PixelIcon.js'
import {duplicateGroups, libraryEntries} from './model.js'
import type {DuplicateGroup, EntryState, LibraryEntry} from './model.js'
import type {Library} from './types.js'
import {bulkMessage, useBulkReview} from './useBulkReview.js'
import './Duplicates.css'

const stateText: Record<EntryState, string> = {approved: 'trafi do kopii', skipped: 'pominięty w kopii', sure: 'czeka na decyzję',
    check: 'czeka na decyzję', queued: 'w analizie', failed: 'błąd analizy', fresh: 'bez analizy'}

function folderOf(relativePath: string): string {
    const parts = relativePath.split(/[\\/]/)
    return parts.length > 1 ? parts.slice(0, -1).join('/') : 'główny folder'
}

/** The copies that are not kept get the "skip" decision, so they never reach the organized copy. Originals stay. */
function skipOthers(group: DuplicateGroup, keepId: number, entries: Map<number, LibraryEntry>): LibraryEntry[] {
    return group.files.filter(file => file.id !== keepId).flatMap(file => entries.get(file.id) ?? [])
}

/** Figma v1+ "06 · Duplikaty", for byte-identical files (same SHA-256). */
export function DuplicatesView({library, onBack}: {library: Library | undefined, onBack: () => void}) {
    const groups = useMemo(() => duplicateGroups(library?.files ?? []), [library])
    const entries = useMemo(() => new Map((library ? libraryEntries(library) : []).map(entry => [entry.file.id, entry])), [library])
    const [current, setCurrent] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const bulk = useBulkReview(library?.batchId ?? null)
    const group = groups.find(candidate => candidate.sha256 === current) ?? groups[0]

    function keep(target: LibraryEntry[]) {
        setNotice(null)
        bulk.mutate({entries: target, action: {decision: 'SKIPPED'}}, {
            onSuccess: result => setNotice(bulkMessage(result)),
            onError: cause => setNotice(cause instanceof Error ? cause.message : 'Nie udało się zapisać decyzji.'),
        })
    }

    if (!library) return null
    if (!group) return <div className="catalog-empty"><h3>Brak duplikatów.</h3><p>Żadne dwa pliki w tym folderze nie mają identycznej treści.</p>
        <button className="secondary-button" onClick={onBack}>Wróć do biblioteki</button></div>

    const extraBytes = groups.reduce((sum, item) => sum + item.extraBytes, 0)
    const extraFiles = groups.reduce((sum, item) => sum + item.files.length - 1, 0)
    const index = groups.indexOf(group)
    const analysed = Boolean(library.batchId)

    return <div className="dups">
        <section className="dups-banner" aria-label="Podsumowanie duplikatów">
            <span className="dups-banner-icon"><PixelIcon name="layers" size={14}/></span>
            <div><strong>{groups.length} {groups.length === 1 ? 'grupa' : 'grup'} · {extraFiles} zbędnych kopii · {formatSize(extraBytes)}</strong>
                <p>Oryginały zostają na dysku. Pominięta kopia po prostu nie trafi do uporządkowanej kopii.</p></div>
            <button className="primary-button" disabled={!analysed || bulk.isPending}
                onClick={() => keep(groups.flatMap(item => {
                    // The oldest copy that can still reach the organized copy; a group with none is left alone.
                    const keeper = item.files.find(file => !['skipped', 'failed'].includes(entries.get(file.id)?.state ?? 'fresh'))
                    return keeper ? skipOthers(item, keeper.id, entries) : []
                }))}>
                <PixelIcon name="check" size={10}/>Zostaw najstarsze we wszystkich</button>
        </section>
        {!analysed && <p className="dups-hint" role="status">Decyzja o kopii wymaga analizy folderu. Uruchom ją w „Do decyzji”.</p>}
        {notice && <div className="lib-notice" role="status">{notice}<button className="text-button" onClick={() => setNotice(null)}>Zamknij</button></div>}
        <div className="dups-body">
            <nav className="dups-list" aria-label="Grupy duplikatów"><h2>Grupy</h2>
                {groups.map(item => <button key={item.sha256} aria-current={item === group ? 'true' : undefined} onClick={() => setCurrent(item.sha256)}>
                    <span className="dups-list-thumb"><CatalogImage key={item.files[0].id} src={imageUrl(item.files[0], 'thumbnail')} name=""/></span>
                    <span className="dups-list-text"><strong>{item.files[0].originalName}</strong>
                        <small>{item.files.length} {item.files.length < 5 ? 'pliki' : 'plików'} · {formatSize(item.files[0].sizeBytes)}</small></span>
                    {item.files.slice(1).every(file => entries.get(file.id)?.state === 'skipped') && <span className="dups-done" title="Kopie pominięte"><PixelIcon name="check" size={10}/></span>}
                </button>)}
            </nav>
            <section className="dups-compare" aria-label="Porównanie">
                <header><p><mark>Grupa {index + 1} z {groups.length}</mark><span>Identyczna treść · ten sam SHA-256</span></p></header>
                <div className="dups-columns">
                    {group.files.map((file, position) => {
                        const entry = entries.get(file.id)
                        const others = skipOthers(group, file.id, entries)
                        const kept = others.every(other => other.state === 'skipped') && entry?.state !== 'skipped'
                        return <article key={file.id} className="dups-file" data-kept={kept}>
                            <header><h3 title={file.originalName}>{file.originalName}</h3><span>{position === 0 ? 'NAJSTARSZY' : 'KOPIA'}</span></header>
                            <div className="dups-preview"><CatalogImage key={file.id} src={imageUrl(file, 'preview')} name={file.originalName}/></div>
                            <dl>
                                <div><dt><PixelIcon name="file" size={10}/>Rozmiar</dt><dd>{formatSize(file.sizeBytes)}</dd></div>
                                <div><dt><PixelIcon name="clock" size={10}/>Zmieniony</dt><dd>{formatDate(file.lastModifiedAt)}</dd></div>
                                <div><dt><PixelIcon name="folder" size={10}/>Folder</dt><dd>{folderOf(file.relativePath)}</dd></div>
                                <div><dt><PixelIcon name="tag" size={10}/>Kopia</dt><dd>{entry ? stateText[entry.state] : '—'}</dd></div>
                            </dl>
                            {/* Skipping cannot be undone from here: it would need an approval with a name and category. */}
                            <button className={kept ? 'primary-button' : 'secondary-button'} disabled={!analysed || bulk.isPending || kept || entry?.state === 'skipped'}
                                title={entry?.state === 'skipped' ? 'Ten plik jest pominięty. Zmień decyzję w „Do decyzji”.' : undefined}
                                onClick={() => keep(others)}><PixelIcon name="check" size={10}/>{kept ? 'Zostawiony' : 'Zostaw ten'}</button>
                        </article>
                    })}
                </div>
            </section>
        </div>
    </div>
}
