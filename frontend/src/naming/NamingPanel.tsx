import {useId, useRef, useState} from 'react'
import type {FormEvent} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {approveNaming, namingKeys, readNaming} from './api.js'
import {fileNameProblem} from './fileNameRules.js'
import type {NamingReview} from './types.js'
import {formatDate} from '../catalog/format.js'
import {Icon} from '../ui/Icon.js'
import styles from './NamingPanel.module.css'
import {useUpdateBlocker} from '../desktop/updates/context.js'

function savedOrProposed(review: NamingReview): string {
    return review.approvalMatchesCatalog ? review.approvedName ?? '' : review.proposedName ?? ''
}

// An edit belongs to one version of the server data; a newer version starts from its own name again.
function versionKey(review: NamingReview): string {
    return `${review.fileId}:${review.analysisId}:${review.revision}`
}

export function NamingPanel({fileId, disabled}: {fileId: number, disabled: boolean}) {
    const queryClient = useQueryClient()
    const reviewQuery = useQuery({
        queryKey: namingKeys.review(fileId),
        queryFn: ({signal}) => readNaming(fileId, signal),
    })
    const approve = useMutation({
        mutationFn: ({review, name}: {review: NamingReview, name: string}) => approveNaming(review, name),
        onSuccess: next => {
            queryClient.setQueryData(namingKeys.review(next.fileId), next)
            setEdit(null)
        },
    })
    const [edit, setEdit] = useState<{version: string, name: string} | null>(null)
    const submitting = useRef(false)
    const inputId = useId()
    const feedbackId = useId()

    const review = reviewQuery.data ?? null
    const baseName = review ? savedOrProposed(review) : ''
    const name = review && edit?.version === versionKey(review) ? edit.name : baseName
    const loading = reviewQuery.isFetching
    const saving = approve.isPending
    const failure = approve.error ?? reviewQuery.error
    const error = failure ? failure.message : null

    const problem = review ? fileNameProblem(name, review.originalName) : null
    const locked = disabled || loading || saving || !review?.analysisMatchesCatalog
    const alreadySaved = review?.approvalMatchesCatalog && name === review.approvedName
    useUpdateBlocker(saving || Boolean(review && name !== baseName), 'Zapisz edytowaną nazwę obrazu przed restartem.')

    function changeName(next: string) {
        if (!review) return
        setEdit({version: versionKey(review), name: next})
        approve.reset()
    }

    function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        // The ref blocks a second click in the same tick, before React re-renders the disabled button.
        if (!review || locked || problem || submitting.current || alreadySaved) return
        submitting.current = true
        approve.mutate({review, name}, {onSettled: () => { submitting.current = false }})
    }

    function reload() {
        approve.reset()
        setEdit(null)
        void reviewQuery.refetch()
    }

    return <section className={styles.panel} aria-label="Propozycja nazwy">
        <h4>Nadaj obrazowi nazwę</h4>
        <p className={styles.hint}>Propozycja łączy słowa z tekstu na obrazie z opisem obrazu, bez nowego wywołania AI i bez tłumaczenia. Popraw ją po swojemu.</p>
        {loading && <p role="status">Przygotowujemy propozycję…</p>}
        {review && !loading && <>
            <p className={styles.original}>Oryginał: <span>{review.originalName}</span></p>
            {!review.analysisMatchesCatalog && <p className={styles.warning}>Analiza dotyczy starszej wersji pliku. Przeanalizuj aktualny obraz przed zatwierdzeniem.</p>}
            {!review.proposedName && review.analysisMatchesCatalog && <p className={styles.hint}>Brakuje tekstu i opisu do utworzenia nazwy. Wpisz własną albo uruchom analizę.</p>}
            {review.proposedName && review.proposalUncertain && !review.approvalMatchesCatalog && <p className={styles.warning} role="status">Niepewna propozycja: powstała z małej ilości informacji. Sprawdź ją przed zatwierdzeniem.</p>}
            {review.approvedName && !review.approvalMatchesCatalog && <p className={styles.warning}>Poprzednia decyzja „{review.approvedName}” dotyczy innej zawartości pliku.</p>}
            <form onSubmit={submit}>
                <label htmlFor={inputId}>Nazwa do zatwierdzenia</label>
                <input id={inputId} value={name} disabled={locked} maxLength={255} autoComplete="off"
                    aria-invalid={Boolean(problem && name)} aria-describedby={feedbackId}
                    onChange={event => changeName(event.target.value)}/>
                <p id={feedbackId} className={problem && name ? styles.error : styles.hint}>
                    {problem && name ? problem : 'Zachowaj rozszerzenie. Zapisujemy decyzję, nie zmieniamy pliku.'}
                </p>
                <div className={styles.actions}>
                    {review.proposedName && <ProposalButton proposed={review.proposedName} current={name}
                        disabled={locked} onUse={changeName}/>}
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
                onClick={reload}>Wczytaj aktualną decyzję</button>
        </div>}
    </section>
}

// A separate component receives the proposal as a non-null prop, so no "!" assertion is needed.
function ProposalButton({proposed, current, disabled, onUse}: {
    proposed: string, current: string, disabled: boolean, onUse: (name: string) => void,
}) {
    return <button type="button" className="text-button" disabled={disabled || current === proposed}
        onClick={() => onUse(proposed)}>
        Użyj propozycji
    </button>
}
