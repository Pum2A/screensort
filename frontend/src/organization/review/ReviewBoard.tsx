import {useCallback, useEffect, useMemo, useState} from 'react'
import type {MouseEvent} from 'react'
import {CatalogImage} from '../../catalog/CatalogImage.js'
import {imageUrl} from '../../catalog/api.js'
import {categoryIcons} from '../../library/categoryIcons.js'
import {categories, countEntries, inReviewTab, isPending, libraryEntries, selectRange} from '../../library/model.js'
import type {BulkAction, LibraryEntry, ReviewTab} from '../../library/model.js'
import {bulkMessage, useBulkReview} from '../../library/useBulkReview.js'
import {useLibrary} from '../../library/useLibrary.js'
import {NamingLanguageSwitch} from '../../naming/NamingLanguageSwitch.js'
import {PixelIcon} from '../../ui/PixelIcon.js'
import type {BatchItem} from '../api.js'
import {ReviewEditor} from './ReviewEditor.js'
import type {EditingState} from './ReviewEditor.js'
import {categoryLabels, isCategory, reviewLabel} from './state.js'
import type {ImageCategory} from './state.js'
import '../../library/Library.css'
import './ReviewBoard.css'

type Props = {folderPath: string, batchId: string, disabled: boolean, active: boolean,
    onSaved: () => void, onEditingChange: (editing: boolean) => void}

const tabs: {tab: ReviewTab, label: string}[] = [{tab: 'pending', label: 'Wszystkie'}, {tab: 'sure', label: 'Pewne'},
    {tab: 'check', label: 'Do sprawdzenia'}, {tab: 'approved', label: 'Zatwierdzone'}, {tab: 'skipped', label: 'Pominięte'},
    {tab: 'queued', label: 'W analizie'}, {tab: 'failed', label: 'Błędy'}]

function shownName(item: BatchItem | null, fallback: string): string {
    return item?.approvedName ?? item?.proposedName ?? fallback
}

/**
 * Figma v1+ "03 · Przegląd hurtowy": every waiting proposal of the folder on one screen. The card
 * category is a pick kept until approval; the side editor is the existing single-image form.
 */
export function ReviewBoard({folderPath, batchId, disabled, active, onSaved, onEditingChange}: Props) {
    const library = useLibrary(folderPath)
    const [tab, setTab] = useState<ReviewTab>('pending')
    const [focusedId, setFocusedId] = useState<number | null>(null)
    const [selected, setSelected] = useState<ReadonlySet<number>>(new Set())
    const [anchor, setAnchor] = useState<number | null>(null)
    const [overrides, setOverrides] = useState<ReadonlyMap<number, ImageCategory>>(new Map())
    const [menuOpen, setMenuOpen] = useState(false)
    const [notice, setNotice] = useState<string | null>(null)
    const [editing, setEditing] = useState<EditingState>({dirty: false, saving: false})

    // A library from another batch (a new analysis just started) must not be reviewed against this one.
    const current = library.data?.batchId === batchId ? library.data : undefined
    // Writes go to the batch on screen, never to a newer one the library already reports.
    const bulk = useBulkReview(current?.batchId ?? null)
    const entries = useMemo(() => current ? libraryEntries(current).filter(entry => entry.item) : [], [current])
    const counts = useMemo(() => countEntries(entries), [entries])
    const visible = useMemo(() => entries.filter(entry => inReviewTab(entry, tab)), [entries, tab])
    const order = useMemo(() => visible.map(entry => entry.file.id), [visible])
    const chosen = visible.filter(entry => selected.has(entry.file.id))
    const busyEditing = editing.dirty || editing.saving
    // A card with an open draft stays in the editor even if it left the tab or the list moved under it.
    const focused = entries.find(entry => entry.file.id === focusedId && (busyEditing || visible.includes(entry))) ?? visible[0]
    const focusedFileId = focused?.file.id ?? null
    const focusedIndex = focused ? visible.indexOf(focused) : -1
    const approved = entries.filter(entry => entry.state === 'approved')
    const sure = entries.filter(entry => entry.state === 'sure')
    const total = entries.length
    const locked = disabled || bulk.isPending || busyEditing

    const reportEditing = useCallback((state: EditingState) => {
        // Pin the card being edited: the default focus is only "first on the list", which can move.
        if (state.dirty || state.saving) setFocusedId(currentId => currentId ?? focusedFileId)
        setEditing(state); onEditingChange(state.dirty || state.saving)
    }, [focusedFileId, onEditingChange])

    function clearPicks(ids: number[]) {
        setOverrides(currentOverrides => {
            const next = new Map(currentOverrides)
            for (const id of ids) next.delete(id)
            return next
        })
    }

    function changeTab(next: ReviewTab) {
        if (busyEditing) { setNotice('Zapisz albo odrzuć zmiany w formularzu po prawej.'); return }
        setTab(next)
    }

    function decide(target: LibraryEntry[], action: BulkAction) {
        setMenuOpen(false); setNotice(null)
        bulk.mutate({entries: target, action}, {
            onSuccess: result => {
                setNotice(bulkMessage(result)); onSaved()
                if (!result.failed) setSelected(new Set())
                // A blocked or failed card keeps its pick: it may be the very thing still missing.
                clearPicks(result.savedIds)
            },
            onError: cause => setNotice(cause instanceof Error ? cause.message : 'Nie udało się zapisać decyzji.'),
        })
    }

    function approve(target: LibraryEntry[]) {
        decide(target, {decision: 'APPROVED', overrides})
    }

    function pick(ids: number[], category: ImageCategory) {
        setOverrides(currentOverrides => new Map([...currentOverrides, ...ids.map(id => [id, category] as const)]))
    }

    function focus(id: number) {
        // The side form holds unsaved text: switching would silently drop it.
        if (busyEditing) { setNotice('Zapisz albo odrzuć zmiany w formularzu po prawej.'); return }
        setFocusedId(id)
    }

    function toggle(id: number, event: MouseEvent) {
        setSelected(currentSelection => {
            if (event.shiftKey) return selectRange(order, anchor, id, currentSelection)
            const next = new Set(currentSelection)
            if (next.has(id)) next.delete(id); else next.add(id)
            return next
        })
        setAnchor(id)
    }

    useEffect(() => {
        if (!active) return
        function onKey(event: KeyboardEvent) {
            // Buttons handle Space and Enter themselves; a second toggle here would hit another card.
            if (event.target instanceof HTMLElement && event.target.closest('input, textarea, select, dialog, button, [role=checkbox]')) return
            if (event.ctrlKey || event.metaKey || event.altKey) {
                if (event.key.toLowerCase() === 'a' && (event.ctrlKey || event.metaKey)) { event.preventDefault(); setSelected(new Set(order)) }
                return
            }
            const digit = Number(event.key)
            if (Number.isInteger(digit) && digit >= 1 && digit <= categories.length && focused) {
                // 1-5: the category of the selected cards, or of the focused one when nothing is selected.
                pick(chosen.length ? chosen.map(entry => entry.file.id) : [focused.file.id], categories[digit - 1])
            } else if (event.key === ' ' && focused) {
                event.preventDefault()
                setSelected(currentSelection => {
                    const next = new Set(currentSelection)
                    if (next.has(focused.file.id)) next.delete(focused.file.id); else next.add(focused.file.id)
                    return next
                })
                setAnchor(focused.file.id)
            } else if (event.key === 'Escape') { setMenuOpen(false); setSelected(new Set()) }
        }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    })

    if (library.isError) return <div className="feedback feedback-error" role="alert"><p>{library.error.message}</p>
        <button className="text-button" onClick={() => void library.refetch()}>Spróbuj ponownie</button></div>
    if (!current) return <p className="inline-loading" role="status">Wczytujemy propozycje…</p>

    return <div className="rv">
        <header className="rv-head">
            <div className="rv-progress"><strong>{counts.approved} z {total} zatwierdzone</strong>
                <div className="lib-bar" aria-hidden="true"><i className="lib-bar-ink" style={{width: `${total ? (counts.approved / total) * 100 : 0}%`}}/>
                    <i className="rv-bar-skip" style={{width: `${total ? (counts.skipped / total) * 100 : 0}%`}}/></div>
                <small>{counts.pending} czeka na decyzję · {counts.skipped} pominiętych · {counts.queued + counts.fresh} w analizie · {counts.failed} błędów</small></div>
            <div className="rv-head-actions">
                <NamingLanguageSwitch disabled={locked}/>
                <button className="primary-button" disabled={locked || sure.length === 0} onClick={() => approve(sure)}>
                    <PixelIcon name="sparkles"/>Zatwierdź pewne ({sure.length})</button>
            </div>
        </header>

        <div className="lib-toolbar">
            <div className="lib-chips" role="group" aria-label="Stan propozycji">
                {tabs.filter(item => item.tab === 'pending' || entries.some(entry => inReviewTab(entry, item.tab))).map(item =>
                    <button key={item.tab} aria-pressed={tab === item.tab} onClick={() => changeTab(item.tab)}>{item.label}
                        <span>{entries.filter(entry => inReviewTab(entry, item.tab)).length}</span></button>)}
            </div>
            <p className="rv-hint">Klik = podgląd · Spacja = zaznacz · 1–5 = kategoria · Shift+klik = zakres</p>
        </div>
        {notice && <div className="lib-notice" role="status">{notice}<button className="text-button" onClick={() => setNotice(null)}>Zamknij</button></div>}

        <div className="rv-body">
            <section aria-label="Propozycje">
                {visible.length === 0 ? <div className="catalog-empty"><h3>{tab !== 'pending' ? 'Nic tu nie ma.' : counts.queued + counts.fresh > 0 ? 'Reszta obrazów jest jeszcze w analizie.' : 'Wszystko zdecydowane.'}</h3>
                    {tab !== 'pending' && <button className="text-button" onClick={() => changeTab('pending')}>Pokaż czekające</button>}</div>
                    : <div className="rv-grid">{visible.map(entry => {
                        const item = entry.item
                        const id = entry.file.id
                        const category = overrides.get(id) ?? entry.category
                        const src = imageUrl(entry.file, 'thumbnail')
                        return <article key={id} className="rv-card" data-focused={focused?.file.id === id} data-selected={selected.has(id)} data-state={entry.state}>
                            <button type="button" className="rv-thumb" aria-label={`Podgląd: ${entry.file.originalName}`}
                                onClick={event => event.shiftKey || event.ctrlKey || event.metaKey ? toggle(id, event) : focus(id)}>
                                <CatalogImage key={src} src={src} name=""/></button>
                            <button type="button" role="checkbox" aria-checked={selected.has(id)} aria-label={`Zaznacz ${entry.file.originalName}`}
                                className="lib-check" onClick={event => toggle(id, event)}>{selected.has(id) && <PixelIcon name="check" size={10}/>}</button>
                            <span className="lib-badges">{entry.state === 'sure' ? <span className="lib-badge lib-badge-lime">AI</span>
                                : entry.state === 'check' ? <span className="lib-badge lib-badge-warn">Sprawdź</span>
                                : entry.state === 'approved' ? <span className="lib-badge lib-badge-ink"><PixelIcon name="check" size={10}/></span>
                                : <span className="lib-badge">{item ? reviewLabel(item) : ''}</span>}</span>
                            <strong title={shownName(item, entry.file.originalName)}>{shownName(item, entry.file.originalName)}</strong>
                            <label className="rv-cat" data-picked={overrides.has(id)}>
                                {category && <PixelIcon name={categoryIcons[category]} size={10}/>}
                                <span className="sr-only">Kategoria dla {entry.file.originalName}</span>
                                <select value={category ?? ''} disabled={locked || !isPending(entry.state) && entry.state !== 'approved'}
                                    onChange={event => { if (isCategory(event.target.value)) pick([id], event.target.value) }}>
                                    {!category && <option value="">Wybierz…</option>}
                                    {categories.map(value => <option key={value} value={value}>{categoryLabels[value]}</option>)}
                                </select><PixelIcon name="chevron-down" size={8}/>
                            </label>
                        </article>
                    })}</div>}
                {approved.length > 0 && tab !== 'approved' && <div className="rv-approved">
                    <span className="lib-check" aria-hidden="true" data-on="true"><PixelIcon name="check" size={10}/></span>
                    <strong>Zatwierdzone: {approved.length}</strong>
                    <span className="rv-approved-strip">{approved.slice(-8).map(entry =>
                        <CatalogImage key={entry.file.id} src={imageUrl(entry.file, 'thumbnail')} name=""/>)}</span>
                    <button className="text-button" onClick={() => changeTab('approved')}>Pokaż</button>
                </div>}
            </section>
            <aside className="rv-side" aria-label="Wybrany obraz">
                {focused?.item && focused.item.status === 'COMPLETED' && focused.item.analysisId !== null
                    ? <ReviewEditor key={focused.file.id + ':' + (focused.item.review?.revision ?? -1)} batchId={batchId} item={focused.item}
                        disabled={disabled || bulk.isPending} onStateChange={reportEditing}
                        onSaved={() => { clearPicks([focused.file.id]); onSaved(); void library.refetch() }}
                        hasNext={focusedIndex >= 0 && focusedIndex < visible.length - 1} onNext={() => focus(visible[focusedIndex + 1].file.id)}/>
                    : <p className="lib-muted">{focused?.item ? focused.item.message ?? reviewLabel(focused.item) : 'Wybierz obraz z siatki.'}</p>}
            </aside>
        </div>

        {chosen.length > 0 && <div className="lib-bulk" role="toolbar" aria-label="Akcje dla zaznaczonych">
            <strong>{chosen.length} zaznaczone</strong>
            <div className="lib-bulk-menu">
                <button aria-expanded={menuOpen} disabled={locked} onClick={() => setMenuOpen(open => !open)}>Kategoria<PixelIcon name="chevron-down" size={10}/></button>
                {menuOpen && <ul role="menu">{categories.map((category, index) => <li key={category}>
                    <button role="menuitem" onClick={() => { pick(chosen.map(entry => entry.file.id), category); setMenuOpen(false) }}>
                        <PixelIcon name={categoryIcons[category]}/>{categoryLabels[category]}<kbd>{index + 1}</kbd></button></li>)}</ul>}
            </div>
            <button disabled={locked} onClick={() => decide(chosen, {decision: 'SKIPPED'})}>Pomiń</button>
            <button className="lib-bulk-main" disabled={locked} onClick={() => approve(chosen)}>
                <PixelIcon name="check" size={10}/>{bulk.isPending ? 'Zapisujemy…' : `Zatwierdź ${chosen.length}`}</button>
            <button className="lib-bulk-close" aria-label="Odznacz wszystkie" onClick={() => {setSelected(new Set()); setMenuOpen(false)}}><PixelIcon name="x" size={10}/></button>
        </div>}
    </div>
}
