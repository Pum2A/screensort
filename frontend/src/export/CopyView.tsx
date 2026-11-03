import {useEffect, useMemo, useState} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {isTauri} from '@tauri-apps/api/core'
import {open} from '@tauri-apps/plugin-dialog'
import {CatalogImage} from '../gallery/CatalogImage.js'
import {imageUrl} from '../gallery/catalogApi.js'
import {formatDate, formatSize} from '../gallery/format.js'
import {categoryIcons} from '../gallery/categoryIcons.js'
import type {LibraryEntry} from '../gallery/model.js'
import {categoryLabels} from '../review/state.js'
import {useUpdateBlocker} from '../tauri/updates/context.js'
import {PixelIcon} from '../ui/PixelIcon.js'
import {exportIssues, exportKeys, latestExport, startExport} from './api.js'
import {copyGroups, copyRoot} from './copyPlan.js'
import './CopyView.css'

const POLL_MS = 800
const PREVIEW = 8

type Props = {entries: LibraryEntry[], batchId: string | null, busyElsewhere: boolean,
    onBusyChange: (busy: boolean) => void, onReview: () => void}

/** Figma v1+ "07 · Kopia uporządkowana": the folders the copy will create, then one button to write it. */
export function CopyView({entries, batchId, busyElsewhere, onBusyChange, onReview}: Props) {
    const queryClient = useQueryClient()
    const groups = useMemo(() => copyGroups(entries), [entries])
    const [destination, setDestination] = useState(() => {try {return localStorage.getItem('screensort.copyTarget') ?? ''} catch {return ''}})
    // null until the owner opens or closes a folder: then the first two folders are shown open.
    const [chosen, setChosen] = useState<ReadonlySet<string> | null>(null)
    const expanded = chosen ?? new Set(groups.slice(0, 2).map(group => group.category))
    const [pickError, setPickError] = useState<string | null>(null)
    const total = groups.reduce((sum, group) => sum + group.files.length, 0)
    const bytes = groups.reduce((sum, group) => sum + group.bytes, 0)
    const pending = entries.filter(entry => entry.state === 'sure' || entry.state === 'check').length

    const jobQuery = useQuery({
        queryKey: exportKeys.latest(batchId ?? ''),
        queryFn: ({signal}) => latestExport(batchId ?? '', signal),
        enabled: batchId !== null,
        refetchInterval: query => query.state.data?.status === 'RUNNING' ? POLL_MS : false,
        refetchIntervalInBackground: true,
    })
    const job = jobQuery.data ?? null
    const issues = useQuery({
        queryKey: exportKeys.issues(job?.id ?? '', 0),
        queryFn: ({signal}) => exportIssues(job?.id ?? '', 0, signal),
        enabled: job !== null && job.status !== 'RUNNING' && job.failed > 0,
    })
    const start = useMutation({
        mutationFn: () => startExport(batchId ?? '', destination),
        onSuccess: created => queryClient.setQueryData(exportKeys.latest(batchId ?? ''), created),
    })
    const running = start.isPending || job?.status === 'RUNNING'
    useUpdateBlocker(running, 'Poczekaj na zakończenie zapisywania uporządkowanej kopii przed restartem.')
    useEffect(() => {onBusyChange(running)}, [running, onBusyChange])
    useEffect(() => () => onBusyChange(false), [onBusyChange])

    async function chooseDestination() {
        setPickError(null)
        try {
            const selected = await open({directory: true, multiple: false, title: 'Wybierz miejsce dla uporządkowanej kopii'})
            if (typeof selected === 'string') {
                setDestination(selected)
                try {localStorage.setItem('screensort.copyTarget', selected)} catch { /* Only a convenience. */ }
            }
        } catch {setPickError('Nie udało się otworzyć wyboru folderu.')}
    }

    function toggle(category: string) {
        const next = new Set(expanded)
        if (next.has(category)) next.delete(category); else next.add(category)
        setChosen(next)
    }

    const allOpen = groups.length > 0 && groups.every(group => expanded.has(group.category))
    const canSave = !!batchId && total > 0 && !!destination.trim() && !running && !busyElsewhere
    const done = job ? job.copied + job.failed : 0
    const error = start.error?.message ?? pickError

    return <div className="copy-page">
        <section className="copy-tree" aria-labelledby="copy-structure">
            <header className="copy-tree-head">
                <h2 id="copy-structure"><span className="copy-mark">Podgląd struktury</span></h2>
                <small>{total} plików · {groups.length} folderów</small>
                <button type="button" className="copy-pill" disabled={groups.length === 0}
                    onClick={() => setChosen(allOpen ? new Set() : new Set(groups.map(group => group.category)))}>{allOpen ? 'Zwiń wszystko' : 'Rozwiń wszystko'}</button>
            </header>
            <div className="copy-destination">
                <PixelIcon name="drive"/>
                {isTauri() ? <code title={destination}>{destination ? copyRoot(destination) : 'Wybierz miejsce na kopię'}</code>
                    : <input value={destination} onChange={event => setDestination(event.target.value)} placeholder="Np. D:\Uporządkowane" aria-label="Folder docelowy"/>}
                {isTauri() && <button type="button" className="copy-link" disabled={running} onClick={() => void chooseDestination()}>{destination ? 'Zmień' : 'Wybierz'}</button>}
            </div>
            {groups.length === 0 ? <div className="copy-empty"><strong>Nic jeszcze nie jest zatwierdzone.</strong>
                <p>Do kopii trafiają tylko obrazy z zatwierdzoną nazwą i kategorią.{pending > 0 && ` ${pending} czeka na decyzję.`}</p>
                <button type="button" className="copy-primary" onClick={onReview}><PixelIcon name="inbox" size={11}/>Przejdź do decyzji</button></div>
                : <ul className="copy-groups">{groups.map(group => {
                    const isOpen = expanded.has(group.category)
                    return <li key={group.category} data-open={isOpen}>
                        <button type="button" className="copy-group" aria-expanded={isOpen} onClick={() => toggle(group.category)}>
                            <PixelIcon name={isOpen ? 'chevron-down' : 'chevron-right'} size={10}/>
                            <span className="copy-group-icon"><PixelIcon name={categoryIcons[group.category]}/></span>
                            <strong>{categoryLabels[group.category]}</strong><small>{group.files.length} plików</small>
                            <span className="copy-group-size">{formatSize(group.bytes)}</span>
                        </button>
                        {isOpen && <div className="copy-files">{group.files.slice(0, PREVIEW).map(({entry, name}) => <figure key={entry.file.id}>
                            <CatalogImage key={entry.file.id} src={imageUrl(entry.file, 'thumbnail')} name=""/>
                            <figcaption><strong title={name}>{name}</strong><small title={entry.file.originalName}>z {entry.file.originalName}</small></figcaption>
                        </figure>)}
                            {group.files.length > PREVIEW && <p className="copy-more">i {group.files.length - PREVIEW} więcej w tym folderze</p>}</div>}
                    </li>
                })}</ul>}
        </section>

        <aside className="copy-side">
            <section className="copy-box" aria-labelledby="copy-settings">
                <h2 id="copy-settings">Ustawienia kopii</h2>
                <div className="copy-setting"><span><strong>Podfoldery dla kategorii</strong><small>{groups.map(group => categoryLabels[group.category]).join('/, ') || 'Gry/, Programowanie/…'}/</small></span><PixelIcon name="check"/></div>
                <div className="copy-setting"><span><strong>Pominięte duplikaty</strong><small>kopie oznaczone w „Duplikaty” nie trafią do kopii</small></span><PixelIcon name="check"/></div>
                <div className="copy-setting"><span><strong>Oryginały nietknięte</strong><small>zawsze kopiujemy, nigdy nie przenosimy</small></span><PixelIcon name="lock"/></div>
            </section>
            <section className="copy-ready" aria-labelledby="copy-ready" aria-live="polite">
                <h2 id="copy-ready">{running ? 'Zapisujemy kopię' : job?.status === 'COMPLETED' && !start.isPending ? 'Ostatnia kopia gotowa' : 'Gotowe do zapisu'}</h2>
                <p><strong>{formatSize(bytes)}</strong><span>{total} plików</span></p>
                {running && job ? <><progress max={Math.max(1, job.total)} value={done} aria-label="Postęp zapisu kopii"/>
                    <small>{done} / {job.total} · {job.failed} pominiętych</small></>
                    : <button type="button" className="copy-save" disabled={!canSave} onClick={() => start.mutate()}>
                        <PixelIcon name="download" size={11}/>Zapisz kopię</button>}
                {!destination && total > 0 && <small>Najpierw wybierz miejsce na kopię.</small>}
                {error && <small className="copy-error" role="alert">{error}</small>}
            </section>
            {job && <section className="copy-box" aria-labelledby="copy-previous">
                <h2 id="copy-previous">Ostatnia kopia</h2>
                <div className="copy-previous"><PixelIcon name="copy"/><span><strong>{formatDate(job.createdAt)}</strong>
                    <small>{job.copied} plików{job.failed ? ` · ${job.failed} pominiętych` : ''}{job.status === 'INTERRUPTED' ? ' · przerwana' : ''}</small></span></div>
                <code className="copy-output" title={job.outputFolder}>{job.outputFolder}</code>
                {!!issues.data?.items.length && <details className="copy-issues"><summary>Pominięte pliki ({job.failed})</summary>
                    <ul>{issues.data.items.map((issue, index) => <li key={index}><strong>{issue.originalName}</strong>{issue.message}</li>)}</ul></details>}
            </section>}
        </aside>
    </div>
}
