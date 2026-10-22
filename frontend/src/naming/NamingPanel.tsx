import {useEffect, useId, useRef, useState} from 'react'
import type {FormEvent} from 'react'
import {approveNaming, readNaming} from './api.js'
import {fileNameProblem} from './fileNameRules.js'
import type {NamingReview} from './types.js'
import {formatDate} from '../catalog/format.js'
import {Icon} from '../ui/Icon.js'
import styles from './NamingPanel.module.css'
import {useUpdateBlocker} from '../desktop/updates/context.js'

export function NamingPanel({fileId, disabled}: {fileId: number, disabled: boolean}) {
    const [review, setReview] = useState<NamingReview | null>(null)
    const [name, setName] = useState('')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [refresh, setRefresh] = useState(0)
    const controller = useRef<AbortController | null>(null)
    const submitting = useRef(false)
    const inputId = useId()
    const feedbackId = useId()

    useEffect(() => {
        const abort = new AbortController()
        controller.current = abort
        readNaming(fileId, abort.signal).then(next => {
            if (abort.signal.aborted) return
            setReview(next)
            setName(next.approvalMatchesCatalog ? next.approvedName ?? '' : next.proposedName ?? '')
        }).catch(cause => {
            if (!abort.signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się odczytać propozycji.')
        }).finally(() => { if (!abort.signal.aborted) setLoading(false) })
        return () => abort.abort()
    }, [fileId, refresh])

    const problem = review ? fileNameProblem(name, review.originalName) : null
    const locked = disabled || loading || saving || !review?.analysisMatchesCatalog
    const alreadySaved = review?.approvalMatchesCatalog && name === review.approvedName
    const baseName = review?.approvalMatchesCatalog ? review.approvedName ?? '' : review?.proposedName ?? ''
    useUpdateBlocker(saving || Boolean(review && name !== baseName), 'Zapisz edytowaną nazwę obrazu przed restartem.')

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        const signal = controller.current?.signal
        if (!review || !signal || signal.aborted || locked || problem || submitting.current || alreadySaved) return
        submitting.current = true
        setSaving(true)
        setError(null)
        try {
            const next = await approveNaming(review, name, signal)
            if (!signal.aborted) { setReview(next); setName(next.approvedName ?? '') }
        } catch (cause) {
            if (!signal.aborted) setError(cause instanceof Error ? cause.message : 'Nie udało się zapisać decyzji.')
        } finally {
            submitting.current = false
            if (!signal.aborted) setSaving(false)
        }
    }

    return <section className={styles.panel} aria-label="Propozycja nazwy">
        <h4>Nadaj obrazowi nazwę</h4>
        <p className={styles.hint}>Propozycja z kilku słów opisu, bez nowego wywołania AI i bez tłumaczenia. Popraw ją po swojemu.</p>
        {loading && <p role="status">Przygotowujemy propozycję…</p>}
        {review && !loading && <>
            <p className={styles.original}>Oryginał: <span>{review.originalName}</span></p>
            {!review.analysisMatchesCatalog && <p className={styles.warning}>Analiza dotyczy starszej wersji pliku. Przeanalizuj aktualny obraz przed zatwierdzeniem.</p>}
            {!review.proposedName && review.analysisMatchesCatalog && <p className={styles.hint}>Brakuje opisu do utworzenia nazwy. Wpisz własną albo uruchom analizę wizualną.</p>}
            {review.approvedName && !review.approvalMatchesCatalog && <p className={styles.warning}>Poprzednia decyzja „{review.approvedName}” dotyczy innej zawartości pliku.</p>}
            <form onSubmit={event => void submit(event)}>
                <label htmlFor={inputId}>Nazwa do zatwierdzenia</label>
                <input id={inputId} value={name} disabled={locked} maxLength={255} autoComplete="off"
                    aria-invalid={Boolean(problem && name)} aria-describedby={feedbackId}
                    onChange={event => { setName(event.target.value); setError(null) }}/>
                <p id={feedbackId} className={problem && name ? styles.error : styles.hint}>
                    {problem && name ? problem : 'Zachowaj rozszerzenie. Zapisujemy decyzję, nie zmieniamy pliku.'}
                </p>
                <div className={styles.actions}>
                    {review.proposedName && <button type="button" className="text-button"
                        disabled={locked || name === review.proposedName} onClick={() => { setName(review.proposedName!); setError(null) }}>
                        Użyj propozycji
                    </button>}
                    <button type="submit" className="primary-button" disabled={locked || Boolean(problem) || alreadySaved}>
                        <Icon name={saving ? 'refresh' : 'check'} className={saving ? 'spin' : ''}/>
                        {saving ? 'Zapisujemy…' : alreadySaved ? 'Nazwa zatwierdzona' : 'Zatwierdź nazwę'}
                    </button>
                </div>
            </form>
            {review.approvedAt && review.approvalMatchesCatalog && <p className={styles.saved} role="status">
                Zapisano w bazie: {review.approvedName} · {formatDate(review.approvedAt)}. Oryginał nadal ma nazwę {review.originalName}.
            </p>}
        </>}
        {error && <div className={styles.warning} role="alert"><p>{error}</p>
            <button type="button" className="text-button" disabled={disabled || loading || saving}
                onClick={() => { setLoading(true); setError(null); setRefresh(value => value + 1) }}>Wczytaj aktualną decyzję</button>
        </div>}
    </section>
}
