import {useEffect, useId, useMemo, useRef, useState} from 'react'
import type {MouseEvent} from 'react'
import {AnimatePresence} from 'motion/react'
import {CatalogImage} from './CatalogImage.js'
import {ImageDetailsDialog} from './details/ImageDetailsDialog.js'
import {imageUrl} from './catalogApi.js'
import {formatSize} from './format.js'
import {categoryLabels} from '../review/state.js'
import type {ImageCategory} from '../review/state.js'
import {PixelIcon} from '../ui/PixelIcon.js'
import {categoryIcons} from './categoryIcons.js'
import {categories, countEntries, duplicateGroups, filterEntries, isPending, lastScan, libraryEntries, selectRange,
    sortEntries, todayCount} from './model.js'
import type {BulkAction, LibraryEntry, LibraryFilter, LibrarySort} from './model.js'
import type {Library} from './libraryTypes.js'
import {bulkMessage, useBulkReview} from './useBulkReview.js'
import './Library.css'

type Props = {
    library: Library | undefined
    loading: boolean
    error: string | null
    onRetry: () => void
    /** False while another view is shown: keyboard shortcuts must not act on a hidden grid. */
    active: boolean
    /** Reads the folder again; new screenshots appear without a separate step. */
    onScan: () => void
    scanDisabled: boolean
    /** One click: start the AI engine if needed, analyse the folder, show the proposals. */
    onOrganize: () => void
    onReview: () => void
    onDuplicates: () => void
}

const stateLabel = {approved: 'Zatwierdzony', skipped: 'Pominięty', sure: 'Pewna propozycja', check: 'Do sprawdzenia',
    queued: 'W analizie', failed: 'Błąd analizy', fresh: 'Bez analizy'} as const

function Badge({entry}: {entry: LibraryEntry}) {
    switch (entry.state) {
        case 'approved': return <span className="lib-badge lib-badge-ink" title={stateLabel.approved}><PixelIcon name="check" size={10}/></span>
        // Most tiles are "sure"; a small dot keeps the grid calm and lets "!" stand out.
        case 'sure': return <span className="lib-ai-dot" title={stateLabel.sure}/>
        case 'check': return <span className="lib-badge lib-badge-warn" title={stateLabel.check}>!</span>
        case 'queued': return <span className="lib-badge" title={stateLabel.queued}>…</span>
        case 'failed': return <span className="lib-badge lib-badge-warn" title={stateLabel.failed}>błąd</span>
        case 'skipped': return <span className="lib-badge" title={stateLabel.skipped}>pominięty</span>
        default: return null
    }
}

function Tile({entry, selected, onToggle, onOpen}: {entry: LibraryEntry, selected: boolean,
    onToggle: (event: MouseEvent) => void, onOpen: (event: MouseEvent<HTMLButtonElement>) => void}) {
    const src = imageUrl(entry.file, 'thumbnail')
    return <div className="lib-tile" data-selected={selected} data-state={entry.state}>
        <button type="button" className="lib-thumb" aria-label={`${entry.file.originalName}, ${stateLabel[entry.state]}`} onClick={onOpen}>
            <CatalogImage key={src} src={src} name=""/>
        </button>
        <button type="button" role="checkbox" aria-checked={selected} aria-label={`Zaznacz ${entry.file.originalName}`}
            className="lib-check" onClick={onToggle}>{selected && <PixelIcon name="check" size={10}/>}</button>
        <span className="lib-badges"><Badge entry={entry}/></span>
        {entry.copies > 1 && <span className="lib-copies" title="Identyczna treść w innym pliku">{entry.copies}×</span>}
        <span className="lib-name" title={entry.file.originalName}>{entry.file.originalName}</span>
    </div>
}

function time(value: string | null): string {
    return value ? new Date(value).toLocaleTimeString('pl-PL', {hour: '2-digit', minute: '2-digit'}) : '—'
}

/** Figma v1+ "01 · Biblioteka": the whole folder on one screen, filters by AI category, bulk decisions. */
export function LibraryView({library, loading, error, onRetry, active, onScan, scanDisabled, onOrganize, onReview, onDuplicates}: Props) {
    const [filter, setFilter] = useState<LibraryFilter>('all')
    const [sort, setSort] = useState<LibrarySort>('newest')
    const [query, setQuery] = useState('')
    const [selected, setSelected] = useState<ReadonlySet<number>>(new Set())
    const [anchor, setAnchor] = useState<number | null>(null)
    const [menuOpen, setMenuOpen] = useState(false)
    const [notice, setNotice] = useState<string | null>(null)
    const [previewId, setPreviewId] = useState<number | null>(null)
    const previewTrigger = useRef<HTMLButtonElement | null>(null)
    const dialogId = useId(), searchId = useId(), sortId = useId()
    const bulk = useBulkReview(library?.batchId ?? null)

    const entries = useMemo(() => library ? libraryEntries(library) : [], [library])
    const counts = useMemo(() => countEntries(entries), [entries])
    const duplicates = useMemo(() => duplicateGroups(library?.files ?? []), [library])
    const visible = useMemo(() => sortEntries(filterEntries(entries, filter, query), sort), [entries, filter, query, sort])
    const order = useMemo(() => visible.map(entry => entry.file.id), [visible])
    // Only what is on screen: a file hidden by a filter or gone after a rescan is never acted on.
    const chosen = visible.filter(entry => selected.has(entry.file.id))
    const preview = entries.find(entry => entry.file.id === previewId)

    useEffect(() => {
        if (!active) return
        function onKey(event: KeyboardEvent) {
            const typing = event.target instanceof HTMLElement && event.target.closest('input, textarea, select, dialog')
            if (typing) return
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') { event.preventDefault(); setSelected(new Set(order)) }
            else if (event.key === 'Escape') { setMenuOpen(false); setSelected(new Set()) }
        }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [active, order])

    function toggle(id: number, event: MouseEvent) {
        setSelected(current => {
            if (event.shiftKey) return selectRange(order, anchor, id, current)
            const next = new Set(current)
            if (next.has(id)) next.delete(id); else next.add(id)
            return next
        })
        setAnchor(id)
    }

    function open(entry: LibraryEntry, event: MouseEvent<HTMLButtonElement>) {
        // With a selection in progress a click extends it, like in a photo app; otherwise it opens the image.
        if (chosen.length > 0 || event.shiftKey || event.ctrlKey || event.metaKey) { toggle(entry.file.id, event); return }
        previewTrigger.current = event.currentTarget
        setPreviewId(entry.file.id)
    }

    function decide(target: LibraryEntry[], action: BulkAction) {
        setMenuOpen(false); setNotice(null)
        bulk.mutate({entries: target, action}, {
            onSuccess: result => { setNotice(bulkMessage(result)); if (!result.failed) setSelected(new Set()) },
            onError: cause => setNotice(cause instanceof Error ? cause.message : 'Nie udało się zapisać decyzji.'),
        })
    }

    if (error) return <div className="catalog-empty catalog-empty-error"><h3>Biblioteka jest niedostępna</h3><p role="alert">{error}</p>
        <button className="secondary-button" onClick={onRetry}>Spróbuj ponownie</button></div>
    if (loading || !library) return <div className="catalog-skeleton" role="status"><span className="sr-only">Ładujemy bibliotekę</span>
        {Array.from({length: 12}, (_, index) => <div className="skeleton-tile" key={index}><span/><i/><i/></div>)}</div>
    if (library.files.length === 0) return <div className="catalog-empty"><h3>{scanDisabled ? 'Wczytujemy obrazy…' : 'Tu zaczyna się porządek.'}</h3>
        <p>{scanDisabled ? 'Obrazy pojawią się tutaj, gdy tylko je znajdziemy.' : 'W tym folderze nie ma jeszcze zrzutów PNG ani JPG. Twoje obrazy zostają dokładnie tam, gdzie są.'}</p>
        <button className="secondary-button" disabled={scanDisabled} onClick={onScan}><PixelIcon name="scan"/>Wczytaj ponownie</button></div>

    const total = entries.length
    const share = (count: number) => `${(count / total) * 100}%`
    const sure = entries.filter(entry => entry.state === 'sure')
    const today = todayCount(library.files)
    const extraBytes = duplicates.reduce((sum, group) => sum + group.extraBytes, 0)
    const bulkBusy = bulk.isPending
    const allVisible = order.length > 0 && order.every(id => selected.has(id))

    return <div className="library">
        <div className="lib-cards">
            <section className="lib-card lib-stats" aria-label="Podsumowanie folderu">
                <div className="lib-total"><span className="lib-card-label">Obrazy w folderze</span>
                    <strong>{total}{today > 0 && <span className="lib-today">+{today} dziś</span>}</strong>
                    <small>skan {time(lastScan(library.files))}</small></div>
                <div className="lib-quick">
                    <button disabled={scanDisabled} onClick={onScan} title="Wczytaj folder ponownie"><span><PixelIcon name="scan" size={14}/></span>Odśwież</button>
                    <button className="lib-quick-main" disabled={scanDisabled} onClick={onOrganize}><span><PixelIcon name="sparkles" size={14}/></span>Uporządkuj z AI</button>
                    <button onClick={onReview}><span><PixelIcon name="download" size={14}/></span>Kopia</button>
                </div>
                <div className="lib-progress">
                    <div className="lib-bar" aria-hidden="true"><i className="lib-bar-ink" style={{width: share(counts.approved)}}/>
                        <i className="lib-bar-lime" style={{width: share(counts.sure)}}/><i className="lib-bar-warn" style={{width: share(counts.check)}}/></div>
                    <p><span><i className="lib-dot-ink"/>Gotowe {counts.approved}</span><span><i className="lib-dot-lime"/>Pewne {counts.sure}</span>
                        <span><i className="lib-dot-warn"/>Sprawdź {counts.check}</span>{counts.fresh + counts.queued > 0 && <span>Bez analizy {counts.fresh + counts.queued}</span>}</p>
                </div>
            </section>
            <section className="lib-card lib-card-sure" aria-label="Pewne propozycje">
                <span className="lib-card-label"><i className="lib-dot-lime" aria-hidden="true"/>Pewne propozycje</span><strong>{counts.sure}</strong>
                {library.batchId ? <><small>Gotowe do zatwierdzenia</small>
                    <button className="primary-button" disabled={sure.length === 0 || bulkBusy} onClick={() => decide(sure, {decision: 'APPROVED'})}>Zatwierdź {sure.length}</button></>
                    : <><small>Folder nie był jeszcze analizowany</small><button className="primary-button" onClick={onReview}>Uruchom analizę</button></>}
            </section>
            <section className="lib-card" aria-label="Duplikaty">
                <span className="lib-card-label">Duplikaty</span><strong>{duplicates.length}</strong>
                <small>{duplicates.length ? `${formatSize(extraBytes)} w powtórzonych plikach` : 'Brak identycznych plików'}</small>
                <button className="secondary-button" disabled={duplicates.length === 0} onClick={onDuplicates}>Porównaj</button>
            </section>
        </div>

        <div className="lib-toolbar">
            <div className="lib-chips" role="group" aria-label="Filtr biblioteki">
                <button aria-pressed={filter === 'all'} onClick={() => setFilter('all')}>Wszystkie<span>{total}</span></button>
                <button aria-pressed={filter === 'pending'} onClick={() => setFilter('pending')}>Do decyzji<span>{counts.pending}</span></button>
                {categories.filter(category => counts.byCategory[category].total > 0).map(category =>
                    <button key={category} aria-pressed={filter === category} onClick={() => setFilter(category)}>{categoryLabels[category]}<span>{counts.byCategory[category].total}</span></button>)}
            </div>
            <div className="lib-tools">
                <label className="lib-search" htmlFor={searchId}><PixelIcon name="search"/><span className="sr-only">Szukaj nazwy</span>
                    <input id={searchId} value={query} placeholder="Szukaj nazwy" onChange={event => setQuery(event.target.value)}/></label>
                <label className="sr-only" htmlFor={sortId}>Kolejność</label>
                <select id={sortId} className="lib-sort" value={sort} onChange={event => setSort(event.target.value === 'name' ? 'name' : 'newest')}>
                    <option value="newest">Najnowsze</option><option value="name">Nazwa</option></select>
            </div>
        </div>

        <div className="lib-body">
            <section className="lib-grid-card" aria-label="Obrazy">
                <header className="lib-grid-head"><p><strong>{visible.length} z {total}</strong><span>Shift+klik zaznacza zakres</span></p>
                    <button className="secondary-button" disabled={visible.length === 0} onClick={() => setSelected(allVisible ? new Set() : new Set(order))}>
                        {allVisible ? 'Odznacz wszystkie' : 'Zaznacz wszystkie'}</button></header>
                {notice && <div className="lib-notice" role="status">{notice}<button className="text-button" onClick={() => setNotice(null)}>Zamknij</button></div>}
                {visible.length === 0 ? <div className="catalog-empty"><h3>Nic tu nie pasuje.</h3>
                    <button className="text-button" onClick={() => {setFilter('all'); setQuery('')}}>Pokaż wszystkie</button></div>
                    : <div className="lib-grid">{visible.map(entry => <Tile key={entry.file.id} entry={entry} selected={selected.has(entry.file.id)}
                        onToggle={event => toggle(entry.file.id, event)} onOpen={event => open(entry, event)}/>)}</div>}
            </section>
            <aside className="lib-side">
                <section className="lib-card lib-categories" aria-label="Kategorie">
                    <h2>Kategorie</h2>
                    {library.batchId ? categories.map(category => {
                        const count = counts.byCategory[category]
                        return <button key={category} className="lib-category" aria-pressed={filter === category} onClick={() => setFilter(filter === category ? 'all' : category)}>
                            <span className="lib-category-icon"><PixelIcon name={categoryIcons[category]}/></span>
                            <span className="lib-category-text"><strong>{categoryLabels[category]}</strong>
                                <small data-pending={count.pending > 0}>{count.pending > 0 ? `${count.pending} do decyzji` : count.total ? 'wszystkie gotowe' : 'brak obrazów'}</small></span>
                            <span className="lib-category-count">{count.total}</span></button>
                    }) : <p className="lib-muted">Kategorie przydziela analiza AI. Uruchom ją w „Do decyzji”.</p>}
                </section>
                <section className="lib-card lib-shortcuts" aria-label="Skróty"><strong>Skróty</strong>
                    <p>Klik podgląd · Shift+klik zakres · Ctrl+A wszystkie · Esc odznacz</p></section>
            </aside>
        </div>

        {chosen.length > 0 && <div className="lib-bulk" role="toolbar" aria-label="Akcje dla zaznaczonych">
            <strong>{chosen.length} zaznaczone</strong>
            <div className="lib-bulk-menu">
                <button aria-expanded={menuOpen} disabled={bulkBusy || !library.batchId} onClick={() => setMenuOpen(open => !open)}>Kategoria<PixelIcon name="chevron-down" size={10}/></button>
                {menuOpen && <ul role="menu">{categories.map((category: ImageCategory) => <li key={category}>
                    <button role="menuitem" onClick={() => decide(chosen, {decision: 'APPROVED', category})}>
                        <PixelIcon name={categoryIcons[category]}/>{categoryLabels[category]}</button></li>)}</ul>}
            </div>
            <button disabled={bulkBusy || !library.batchId} onClick={() => decide(chosen, {decision: 'SKIPPED'})}>Pomiń</button>
            <button className="lib-bulk-main" disabled={bulkBusy || !library.batchId || !chosen.some(entry => isPending(entry.state))}
                onClick={() => decide(chosen, {decision: 'APPROVED'})}><PixelIcon name="check" size={10}/>{bulkBusy ? 'Zapisujemy…' : `Zatwierdź ${chosen.length}`}</button>
            <button className="lib-bulk-close" aria-label="Odznacz wszystkie" onClick={() => {setSelected(new Set()); setMenuOpen(false)}}><PixelIcon name="x" size={10}/></button>
        </div>}

        <AnimatePresence>{preview && <ImageDetailsDialog key={preview.file.id} id={dialogId} file={preview.file}
            trigger={previewTrigger} onClose={() => setPreviewId(null)}/>}</AnimatePresence>
    </div>
}
