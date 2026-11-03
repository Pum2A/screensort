import {useEffect, useMemo, useRef, useState} from 'react'
import type {RefObject} from 'react'
import * as m from 'motion/react-m'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {CatalogImage} from '../CatalogImage.js'
import {imageUrl} from '../catalogApi.js'
import {folderName, formatDate, formatSize} from '../format.js'
import {libraryKeys} from '../libraryApi.js'
import type {LibraryEntry} from '../model.js'
import {categories, isPending} from '../model.js'
import {analysisKeys, latestAnalysis} from '../../analysis/api.js'
import type {SavedAnalysis} from '../../analysis/types.js'
import {saveReview} from '../../review/api.js'
import {AnalysisPanel} from '../../analysis/AnalysisPanel.js'
import {categoryLabels, draftFor, reviewWrite} from '../../review/state.js'
import type {ImageCategory} from '../../review/state.js'
import {fileNameProblem} from '../../suggestions/fileNameRules.js'
import {Logo} from '../../ui/Logo.js'
import {PixelIcon} from '../../ui/PixelIcon.js'
import './ImageViewer.css'

type Props = {
    entries: LibraryEntry[]
    fileId: number
    batchId: string | null
    trigger: RefObject<HTMLElement | null>
    onNavigate: (fileId: number) => void
    onClose: () => void
}

const shortLabels: Record<ImageCategory, string> = {PROGRAMMING: 'Kod', DOCUMENTS: 'Dok.', GAMES: 'Gry', PHOTOGRAPHS: 'Zdjęcia', OTHER: 'Pozost.'}
const FILMSTRIP = 5
const ZOOMS = [0.5, 0.75, 1, 1.5, 2, 3]

function splitName(name: string): [string, string] {
    const dot = name.lastIndexOf('.')
    return dot > 0 ? [name.slice(0, dot), name.slice(dot)] : [name, '']
}

function time(value: string): string {
    return new Date(value).toLocaleTimeString('pl-PL', {hour: '2-digit', minute: '2-digit'})
}

/** Figma v1+ "02 · Szczegóły zdjęcia": the image large, the decision beside it, neighbours below. */
export function ImageViewer({entries, fileId, batchId, trigger, onNavigate, onClose}: Props) {
    const index = entries.findIndex(entry => entry.file.id === fileId)
    const entry = entries[index]
    const root = useRef<HTMLDivElement>(null)
    const stage = useRef<HTMLDivElement>(null)
    const [zoom, setZoom] = useState<number | 'fit'>('fit')
    const [rotation, setRotation] = useState(0)
    const go = (step: number) => {
        const next = entries[index + step]
        if (next) {onNavigate(next.file.id); setZoom('fit'); setRotation(0)}
    }

    // Focus moves in, the page behind stops scrolling, and focus returns to the tile on close.
    useEffect(() => {
        const opener = trigger.current
        const overflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        root.current?.focus({preventScroll: true})
        return () => {
            document.body.style.overflow = overflow
            if (opener?.isConnected) opener.focus({preventScroll: true})
        }
    }, [trigger])

    if (!entry) return null
    const file = entry.file
    const [base, extension] = splitName(file.originalName)
    const strip = entries.slice(Math.max(0, index - FILMSTRIP), index + FILMSTRIP + 1)
    const scale = zoom === 'fit' ? 1 : zoom

    function zoomBy(direction: 1 | -1) {
        const current = zoom === 'fit' ? 1 : zoom
        const next = direction > 0 ? ZOOMS.find(value => value > current) : ZOOMS.toReversed().find(value => value < current)
        if (next) setZoom(next)
    }

    return <m.div ref={root} className="image-viewer" role="dialog" aria-modal="true" aria-label={`Szczegóły: ${file.originalName}`}
        tabIndex={-1} initial={{opacity: 0}} animate={{opacity: 1}} exit={{opacity: 0}}
        onKeyDown={event => {
            const typing = event.target instanceof HTMLInputElement
            if (event.key === 'Escape') {event.preventDefault(); if (typing) root.current?.focus(); else onClose()}
            else if (typing) return
            else if (event.key === 'ArrowLeft') {event.preventDefault(); go(-1)}
            else if (event.key === 'ArrowRight') {event.preventDefault(); go(1)}
            else if (event.key === ' ') {event.preventDefault(); setZoom(value => value === 'fit' ? 2 : 'fit')}
            else if (event.key === '+' || event.key === '=') zoomBy(1)
            else if (event.key === '-') zoomBy(-1)
        }}>
        <header className="viewer-top">
            <Logo markSize={22}/>
            <span className="viewer-divider" aria-hidden="true"/>
            <button type="button" className="viewer-pill" onClick={onClose}><PixelIcon name="chevron-left"/>Biblioteka<kbd>Esc</kbd></button>
            <span className="viewer-path">{folderName(file.sourceFolder)}{entry.category ? ` / ${categoryLabels[entry.category]}` : ''} /</span>
            <h2 title={file.originalName}>{file.originalName}</h2>
            <span className="viewer-badge">{file.extension.replace('.', '').toUpperCase()}</span>
            <span className="viewer-spacer"/>
            <span className="viewer-count">{index + 1} z {entries.length}</span>
            <button type="button" className="viewer-round" aria-label="Poprzedni obraz" disabled={index === 0} onClick={() => go(-1)}><PixelIcon name="chevron-left" size={14}/></button>
            <button type="button" className="viewer-round" aria-label="Następny obraz" disabled={index === entries.length - 1} onClick={() => go(1)}><PixelIcon name="chevron-right" size={14}/></button>
        </header>
        <div className="viewer-body">
            <div className="viewer-column">
                <div ref={stage} className="viewer-stage" data-zoomed={zoom !== 'fit'}>
                    <p className="viewer-hint"><kbd>Spacja</kbd>powiększ · <kbd>←</kbd><kbd>→</kbd>poprzedni i następny</p>
                    <div className="viewer-image" style={{transform: `rotate(${rotation}deg) scale(${scale})`}}>
                        <CatalogImage key={file.id} src={imageUrl(file, 'preview')} name={file.originalName} preview/>
                    </div>
                    <div className="viewer-tools" role="toolbar" aria-label="Widok obrazu">
                        <button type="button" aria-label="Pomniejsz" onClick={() => zoomBy(-1)}><PixelIcon name="zoom-out" size={14}/></button>
                        <button type="button" className="viewer-zoom" onClick={() => setZoom('fit')} title="Dopasuj do okna">{zoom === 'fit' ? 'Cały' : `${Math.round(scale * 100)}%`}</button>
                        <button type="button" aria-label="Powiększ" onClick={() => zoomBy(1)}><PixelIcon name="zoom-in" size={14}/></button>
                        <button type="button" aria-label="Obróć" onClick={() => setRotation(value => (value + 90) % 360)}><PixelIcon name="rotate" size={14}/></button>
                        <button type="button" aria-label="Pełny ekran" onClick={() => void stage.current?.requestFullscreen?.().catch(() => undefined)}><PixelIcon name="maximize" size={14}/></button>
                    </div>
                </div>
                <nav className="viewer-strip" aria-label="Sąsiednie obrazy">
                    <button type="button" className="viewer-round" aria-label="Poprzedni obraz" disabled={index === 0} onClick={() => go(-1)}><PixelIcon name="chevron-left"/></button>
                    <div className="viewer-strip-items">{strip.map(other => <button key={other.file.id} type="button"
                        aria-current={other.file.id === fileId ? 'true' : undefined} aria-label={other.file.originalName}
                        onClick={() => {onNavigate(other.file.id); setZoom('fit'); setRotation(0)}}>
                        <CatalogImage key={other.file.id} src={imageUrl(other.file, 'thumbnail')} name=""/>
                        {other.state === 'sure' && <span className="viewer-strip-badge">pewna</span>}
                        {other.state === 'check' && <span className="viewer-strip-badge viewer-strip-warn">!</span>}
                    </button>)}</div>
                    <button type="button" className="viewer-round" aria-label="Następny obraz" disabled={index === entries.length - 1} onClick={() => go(1)}><PixelIcon name="chevron-right"/></button>
                </nav>
            </div>
            <aside className="viewer-side" aria-label="Propozycja i dane obrazu">
                <ProposalCard key={`${file.id}:${entry.item?.namingRevision}:${entry.item?.review?.revision}`} entry={entry} batchId={batchId}
                    base={base} extension={extension} onDecided={() => go(1)}/>
                <AnalysisCards fileId={file.id}/>
                <section className="viewer-card">
                    <h3>Plik</h3>
                    <dl className="viewer-facts">
                        <div><dt><PixelIcon name="file" size={11}/>Rozmiar</dt><dd>{formatSize(file.sizeBytes)} · {file.extension.replace('.', '').toUpperCase()}</dd></div>
                        <div><dt><PixelIcon name="clock" size={11}/>Zmieniony</dt><dd>{formatDate(file.lastModifiedAt)}</dd></div>
                        <div><dt><PixelIcon name="folder" size={11}/>Folder</dt><dd title={file.relativePath}>{folderName(file.sourceFolder)}</dd></div>
                        <div><dt><PixelIcon name="lock" size={11}/>Oryginał</dt><dd>nigdy nie zmieniany</dd></div>
                        {entry.copies > 1 && <div><dt><PixelIcon name="layers" size={11}/>Kopie</dt><dd>{entry.copies}× ta sama treść</dd></div>}
                    </dl>
                </section>
                <History entry={entry}/>
            </aside>
        </div>
    </m.div>
}

function ProposalCard({entry, batchId, base, extension, onDecided}: {entry: LibraryEntry, batchId: string | null,
    base: string, extension: string, onDecided: () => void}) {
    const queryClient = useQueryClient()
    const item = entry.item
    const draft = item ? draftFor(item) : null
    const [name, setName] = useState(() => draft ? splitName(draft.name)[0] : base)
    const [category, setCategory] = useState<string>(draft?.category ?? '')
    const decided = entry.state === 'approved' || entry.state === 'skipped'
    const fullName = name.trim() + extension
    const problem = item ? fileNameProblem(fullName, item.originalName) : null
    const save = useMutation({
        mutationFn: (decision: 'APPROVED' | 'SKIPPED') => {
            if (!batchId || !item || !draft) throw new Error('Najpierw przeanalizuj folder.')
            return saveReview(batchId, item.fileId, reviewWrite({...draft, name: fullName, category}, decision))
        },
        onSuccess: () => {
            void queryClient.invalidateQueries({queryKey: libraryKeys.all})
            void queryClient.invalidateQueries({queryKey: ['organization']})
            onDecided()
        },
    })
    const canApprove = !!item?.analysisId && !!category && !problem && !save.isPending

    // Keyboard on the whole viewer: Enter approves, S skips, 1-5 picks the category.
    useEffect(() => {
        function onKey(event: KeyboardEvent) {
            if (!item || event.ctrlKey || event.metaKey || event.altKey) return
            const typing = event.target instanceof HTMLInputElement
            if (event.key === 'Enter' && canApprove) {event.preventDefault(); save.mutate('APPROVED')}
            else if (typing) return
            else if (event.key.toLowerCase() === 's' && !save.isPending) save.mutate('SKIPPED')
            else if (/^[1-5]$/.test(event.key)) setCategory(categories[Number(event.key) - 1])
        }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [item, canApprove, save])

    const badge = entry.state === 'approved' ? 'ZATWIERDZONA' : entry.state === 'skipped' ? 'POMINIĘTA'
        : entry.state === 'sure' ? 'PEWNA' : entry.state === 'check' ? 'SPRAWDŹ' : null
    if (!item || !isPending(entry.state) && !decided) return <section className="viewer-card viewer-proposal">
        <div className="viewer-card-head"><span className="viewer-mark">Propozycja</span></div>
        <p className="viewer-empty">{entry.state === 'queued' ? 'AI analizuje ten obraz. Propozycja pojawi się za chwilę.'
            : entry.state === 'failed' ? `Analiza nie powiodła się${item?.message ? `: ${item.message}` : '.'} Możesz spróbować tylko dla tego obrazu.`
                : 'Ten obraz nie ma jeszcze analizy. „Uporządkuj z AI” w bibliotece obejmie cały folder; poniżej przeanalizujesz tylko ten obraz.'}</p>
        {entry.state !== 'queued' && <AnalysisPanel key={entry.file.id} fileId={entry.file.id}/>}
    </section>

    return <section className="viewer-card viewer-proposal" aria-label="Propozycja nazwy i kategorii">
        <div className="viewer-card-head"><span className="viewer-mark">Propozycja</span>
            {badge && <span className={'viewer-tag' + (entry.state === 'check' ? ' viewer-tag-warn' : decided ? ' viewer-tag-ink' : '')}>{badge}</span>}</div>
        <label className="viewer-name" data-invalid={!!problem}>
            <span className="sr-only">Nazwa pliku</span>
            <input value={name} onChange={event => setName(event.target.value)} spellCheck={false} disabled={save.isPending}/>
            <span aria-hidden="true">{extension}</span><PixelIcon name="pencil" size={11}/>
        </label>
        {problem && <small className="viewer-problem" role="alert">{problem}</small>}
        <div className="viewer-segments" role="radiogroup" aria-label="Kategoria">{categories.map((value, position) =>
            <button key={value} type="button" role="radio" aria-checked={category === value} title={categoryLabels[value]}
                onClick={() => setCategory(value)} disabled={save.isPending}>{shortLabels[value]}<kbd>{position + 1}</kbd></button>)}</div>
        <div className="viewer-actions">
            <button type="button" className="viewer-pill" disabled={save.isPending} onClick={() => save.mutate('SKIPPED')}>Pomiń<kbd>S</kbd></button>
            <button type="button" className="viewer-approve" disabled={!canApprove} onClick={() => save.mutate('APPROVED')}>
                <PixelIcon name="check"/>{save.isPending ? 'Zapisujemy…' : decided ? 'Zapisz zmianę' : 'Zatwierdź'}<kbd>Enter</kbd></button>
        </div>
        {save.isError && <small className="viewer-problem" role="alert">{save.error.message}</small>}
    </section>
}

function AnalysisCards({fileId}: {fileId: number}) {
    const saved = useQuery({queryKey: analysisKeys.latest(fileId), queryFn: ({signal}) => latestAnalysis(fileId, signal)})
    const [copied, setCopied] = useState(false)
    const result = saved.data?.result
    const visual = result?.visual.status === 'COMPLETED' ? result.visual.result : null
    const ocr = result?.ocr.status === 'COMPLETED' ? result.ocr.result : null
    const lines = useMemo(() => ocr?.text.split(/\r?\n/).map(line => line.trim()).filter(Boolean).slice(0, 12) ?? [], [ocr])
    if (!saved.data) return null
    return <>
        {visual && <section className="viewer-card">
            <h3>Co widzi AI</h3>
            {visual.subject ? <dl className="viewer-facts viewer-subject">
                <div><dt>Aplikacja</dt><dd>{visual.subject.app}</dd></div>
                <div><dt>Temat</dt><dd>{visual.subject.topic_pl}</dd></div>
                <div><dt>Kategoria AI</dt><dd>{visual.subject.category}</dd></div>
            </dl> : <p className="viewer-text">{visual.description}</p>}
            <small className="viewer-meta">{visual.model} · {visual.device.toUpperCase()} · {(visual.duration_ms / 1000).toFixed(1).replace('.', ',')} s</small>
        </section>}
        {ocr && lines.length > 0 && <section className="viewer-card">
            <div className="viewer-card-head"><h3>Tekst z obrazu</h3>
                <button type="button" className="viewer-link" onClick={() => void navigator.clipboard?.writeText(ocr.text).then(() => setCopied(true))}>
                    <PixelIcon name="copy" size={10}/>{copied ? 'Skopiowano' : 'Kopiuj'}</button></div>
            <ol className="viewer-ocr">{lines.map((line, position) => <li key={position}><span>{position + 1}</span>{line}</li>)}</ol>
            <small className="viewer-meta">{ocr.engine} · {Math.round(ocr.duration_ms)} ms</small>
        </section>}
    </>
}

function History({entry}: {entry: LibraryEntry}) {
    const saved = useQuery<SavedAnalysis | null>({queryKey: analysisKeys.latest(entry.file.id),
        queryFn: ({signal}) => latestAnalysis(entry.file.id, signal)})
    const review = entry.item?.review?.matchesCatalog ? entry.item.review : null
    return <section className="viewer-card viewer-history">
        <h3>Historia</h3>
        <ol>
            <li><i/>Wczytano<time>{time(entry.file.firstDiscoveredAt)}</time></li>
            {saved.data && <li><i/>Przeanalizowano<time>{time(saved.data.analyzedAt)}</time></li>}
            {review ? <li><i/>{review.decision === 'APPROVED' ? 'Zatwierdzono' : 'Pominięto'}<time>{time(review.reviewedAt)}</time></li>
                : isPending(entry.state) && <li className="viewer-now"><i/>Czeka na Twoją decyzję<time>teraz</time></li>}
        </ol>
    </section>
}
