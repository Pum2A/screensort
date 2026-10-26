import {useEffect, useId, useRef, useState} from 'react'
import type {FormEvent} from 'react'
import {Check, ChevronRight, CircleAlert, LoaderCircle, RotateCcw, SkipForward} from 'lucide-react'
import {CatalogImage} from '../../catalog/CatalogImage.js'
import {fileNameProblem} from '../../naming/fileNameRules.js'
import type {BatchItem} from '../api.js'
import {readReview, saveReview} from './api.js'
import {categoryLabels, draftChanged, draftFor, isCategory, reviewImageUrl, reviewWrite, serverChanged} from './state.js'
import {ReviewEvidence} from './ReviewEvidence.js'
import styles from './ReviewEditor.module.css'

export type EditingState = {dirty: boolean; saving: boolean}
type Props = {batchId: string; item: BatchItem; disabled: boolean; onSaved: (item: BatchItem) => void;
    onStateChange: (state: EditingState) => void; onNext: () => void; hasNext: boolean}

// The parent keys by file ID. Polling must not replace a draft or silently advance its revisions.
export function ReviewEditor({batchId, item, disabled, onSaved, onStateChange, onNext, hasNext}: Props) {
    const [draft, setDraft] = useState(() => draftFor(item))
    const [operation, setOperation] = useState<'SAVE' | 'RELOAD' | null>(null)
    const saving = operation !== null
    const [error, setError] = useState<string | null>(null)
    const pending = useRef(false)
    const controller = useRef<AbortController | null>(null)
    const nameId = useId(), categoryId = useId(), feedbackId = useId()
    const dirty = draftChanged(draft)
    const changed = serverChanged(draft, item)
    const nameProblem = fileNameProblem(draft.name, draft.base.originalName)
    const locked = disabled || saving || changed || !item.analysisMatchesCatalog
    const approved = draft.base.review?.matchesCatalog && draft.base.review.decision === 'APPROVED'
        && draft.name === draft.base.approvedName && draft.category === draft.base.review.category

    useEffect(() => { onStateChange({dirty, saving}) }, [dirty, saving, onStateChange])
    useEffect(() => () => { controller.current?.abort(); onStateChange({dirty: false, saving: false}) }, [onStateChange])

    async function submit(decision: 'APPROVED' | 'SKIPPED') {
        if (locked || pending.current || decision === 'APPROVED' && (nameProblem || !isCategory(draft.category))) return
        pending.current = true; setOperation('SAVE'); setError(null)
        const request = new AbortController(); controller.current = request
        try {
            const result = await saveReview(batchId, item.fileId, reviewWrite(draft, decision), request.signal)
            if (request.signal.aborted) return
            setDraft(draftFor(result)); onSaved(result)
        } catch (cause) {
            if (!request.signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się zapisać decyzji.')
        } finally {
            pending.current = false
            if (!request.signal.aborted) setOperation(null)
        }
    }

    async function reload() {
        if (pending.current || disabled) return
        pending.current = true; setOperation('RELOAD'); setError(null)
        const request = new AbortController(); controller.current = request
        try {
            const result = await readReview(batchId, item.fileId, request.signal)
            if (!request.signal.aborted) { setDraft(draftFor(result)); onSaved(result) }
        } catch (cause) {
            if (!request.signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się odświeżyć decyzji.')
        } finally {
            pending.current = false
            if (!request.signal.aborted) setOperation(null)
        }
    }

    const src = reviewImageUrl(item, 'preview')
    return <section className={styles.editor} aria-label={`Decyzja dla ${item.originalName}`}>
        <header className={styles.heading}><h4>Nazwa i kategoria</h4><span>Twoja decyzja przed zapisaniem kopii</span></header>
        <div className={styles.preview}><CatalogImage key={src} src={src} name={item.originalName} preview/></div>
        <p className={styles.original}><span>Oryginalna nazwa</span>{item.originalName}</p>
        <p className={styles.hint}>Sprawdź propozycję i popraw ją, jeśli nie oddaje zawartości obrazu.</p>
        {item.proposedName && item.proposalUncertain && !item.approvedName && <p role="status" className="feedback feedback-warning">Niepewna propozycja nazwy: powstała z małej ilości informacji. Decyzja należy do Ciebie.</p>}
        {item.analysisId != null && <ReviewEvidence key={item.analysisId} fileId={item.fileId} analysisId={item.analysisId} sourceHash={item.sourceSha256}/>}
        {changed && <p role="status" className="my-3 rounded-lg bg-amber-50 p-3 text-amber-900">Wynik lub decyzja zmieniły się na serwerze. Edycji nie nadpisano. Wczytaj aktualny formularz.</p>}
        {!item.analysisMatchesCatalog && <p role="status" className="my-3 rounded-lg bg-amber-50 p-3 text-amber-900">Ta analiza nie odpowiada aktualnym danym katalogu. Zeskanuj i przeanalizuj obraz ponownie.</p>}
        {item.review && !item.review.matchesCatalog && <p className="text-sm text-amber-900">Poprzednia decyzja wymaga ponownego zatwierdzenia.</p>}
        <form className={styles.form} onSubmit={(event: FormEvent<HTMLFormElement>) => { event.preventDefault(); void submit('APPROVED') }}>
            <label className="mb-1 block font-semibold" htmlFor={nameId}>Nazwa pliku</label>
            <input id={nameId} value={draft.name} disabled={locked} maxLength={255} autoComplete="off"
                className="w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-900 focus:border-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                aria-invalid={Boolean(nameProblem)} aria-describedby={feedbackId}
                onChange={event => {setDraft({...draft, name: event.target.value}); setError(null)}}/>
            <p id={feedbackId} className={`mt-1 text-sm ${nameProblem ? 'text-red-700' : 'text-stone-500'}`}>
                {nameProblem ?? 'Sama nazwa, bez ścieżki. Zachowujemy oryginalne rozszerzenie.'}</p>
            <label className="mb-1 mt-4 block font-semibold" htmlFor={categoryId}>Kategoria</label>
            <select id={categoryId} value={draft.category} disabled={locked}
                className="w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-900 focus:border-emerald-700 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                onChange={event => {setDraft({...draft, category: event.target.value}); setError(null)}}>
                <option value="">Wybierz kategorię…</option>
                {Object.entries(categoryLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
            <div className="mt-5 flex flex-wrap gap-2">
                <button type="submit" className="primary-button" disabled={locked || Boolean(nameProblem) || !isCategory(draft.category) || approved}>
                    {saving ? <LoaderCircle size={18} className="spin"/> : <Check size={18}/>}{saving ? operation === 'RELOAD' ? 'Wczytujemy…' : 'Zapisujemy…' : approved ? 'Zatwierdzono' : 'Zatwierdź nazwę i kategorię'}
                </button>
                <button type="button" className="secondary-button" disabled={locked || item.review?.matchesCatalog && item.review.decision === 'SKIPPED'} onClick={() => void submit('SKIPPED')}>
                    <SkipForward size={18}/>Pomiń ten plik
                </button>
            </div>
        </form>
        {(dirty || changed || error) && <button type="button" className="text-button mt-3" disabled={saving || disabled}
            onClick={() => { if (changed || error) void reload(); else {setDraft(draftFor(item)); setError(null)} }}><RotateCcw size={16}/>{changed || error ? 'Wczytaj aktualny formularz (odrzuć edycję)' : 'Odrzuć edycję'}</button>}
        {error && <p role="alert" className="mt-3 flex items-start gap-2 rounded-lg bg-red-50 p-3 text-red-800"><CircleAlert size={18} className="shrink-0"/>{error}</p>}
        {dirty && <p role="status" className="mt-2 text-sm text-amber-900">Zmiany w formularzu nie są jeszcze zapisane.</p>}
        {item.review?.matchesCatalog && !changed && !dirty && <p role="status" className={`mt-3 rounded-lg p-3 text-sm ${item.review.decision === 'APPROVED' ? 'bg-emerald-50 text-emerald-800' : 'bg-stone-100 text-stone-700'}`}>
            {item.review.decision === 'APPROVED' ? 'Nazwa i kategoria zapisane w bazie. Oryginał pozostaje bez zmian.' : 'Plik pominięty. Możesz później wrócić i go zatwierdzić.'}</p>}
        <button type="button" className="secondary-button mt-4" disabled={!hasNext || dirty || saving} onClick={onNext}>Następny obraz<ChevronRight size={18}/></button>
    </section>
}
