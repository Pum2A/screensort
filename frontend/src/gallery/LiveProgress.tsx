import {useEffect, useRef, useState} from 'react'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {controlBatch, latestBatch, organizationKeys} from '../analysis/batchApi.js'
import type {Batch} from '../analysis/batchApi.js'
import {PixelIcon} from '../ui/PixelIcon.js'
import type {LibraryEntry} from './model.js'
import {etaLabel, feedEvents} from './live.js'
import type {FeedEvent} from './live.js'

const POLL_MS = 1500

export function isLive(batch: Batch | null | undefined): batch is Batch {
    return !!batch && (batch.status === 'RUNNING' || batch.processing > 0)
}

/** The folder's analysis while it runs; the same query the "Do decyzji" panel polls. */
export function useLiveBatch(folderPath: string) {
    return useQuery({
        queryKey: organizationKeys.latest(folderPath),
        queryFn: ({signal}) => latestBatch(folderPath, signal),
        refetchInterval: query => isLive(query.state.data) ? POLL_MS : false,
    })
}

/** Remembers when each tile got its result, newest first, while the analysis runs. */
export function useLiveFeed(entries: LibraryEntry[], live: boolean): FeedEvent[] {
    const previous = useRef<Map<number, LibraryEntry['state']> | null>(null)
    const [events, setEvents] = useState<FeedEvent[]>([])
    useEffect(() => {
        const before = previous.current
        previous.current = new Map(entries.map(entry => [entry.file.id, entry.state]))
        if (!live || !before) return
        const fresh = feedEvents(before, entries, Date.now())
        if (fresh.length) setEvents(current => [...fresh, ...current].slice(0, 6))
    }, [entries, live])
    return live ? events : []
}

type Props = {batch: Batch, sure: LibraryEntry[], approveBusy: boolean, onApproveSure: () => void, onShowFailed: () => void}

/** Figma v1+ "04 · Skanowanie": the top row while the AI works through the folder. */
export function LiveProgress({batch, sure, approveBusy, onApproveSure, onShowFailed}: Props) {
    const queryClient = useQueryClient()
    const started = useRef({at: Date.now(), done: batch.completed + batch.failed})
    const pause = useMutation({
        mutationFn: () => controlBatch(batch.id, 'PAUSE'),
        onSuccess: next => queryClient.setQueryData(organizationKeys.latest(batch.folderPath), next),
    })
    const done = batch.completed + batch.failed
    const percent = batch.total ? Math.round(done / batch.total * 100) : 0
    const eta = etaLabel(done - started.current.done, Date.now() - started.current.at, batch.total - done)
    const radius = 26, length = 2 * Math.PI * radius
    return <div className="lib-cards lib-live" aria-live="polite">
        <section className="lib-card lib-live-main" aria-label="Postęp analizy">
            <svg className="lib-ring" width="64" height="64" viewBox="0 0 64 64" role="img" aria-label={`${percent}% gotowe`}>
                <circle cx="32" cy="32" r={radius}/>
                <circle cx="32" cy="32" r={radius} strokeDasharray={length} strokeDashoffset={length * (1 - percent / 100)}/>
            </svg>
            <div className="lib-live-count"><span className="lib-card-label">{batch.status === 'RUNNING' ? 'AI analizuje' : 'Kończymy bieżący obraz'}</span>
                <strong>{done}<span> / {batch.total}</span>{eta && <em>{eta}</em>}</strong></div>
            <ul className="lib-stages">
                <li data-done="true"><PixelIcon name="check"/><span>Pliki<small>{batch.total}</small></span></li>
                <li data-active={batch.processing > 0}><PixelIcon name="sparkles"/><span>Analiza<small>{batch.completed}/{batch.total}</small></span></li>
                <li><PixelIcon name="tag"/><span>Nazwy<small>{batch.completed}</small></span></li>
            </ul>
            <button type="button" className="secondary-button lib-live-pause" disabled={batch.status !== 'RUNNING' || pause.isPending}
                onClick={() => pause.mutate()}><PixelIcon name="x" size={10}/>Wstrzymaj</button>
        </section>
        <section className="lib-card lib-live-ready" aria-label="Gotowe do decyzji">
            <span className="lib-card-label">Gotowe teraz</span><strong>{sure.length}</strong>
            <small>Zatwierdź pewne, zanim AI skończy</small>
            <button className="primary-button" disabled={sure.length === 0 || approveBusy} onClick={onApproveSure}>Zatwierdź pewne · {sure.length}</button>
        </section>
        <section className="lib-card" aria-label="Błędy analizy">
            <span className="lib-card-label">Pominięte</span><strong>{batch.failed}</strong>
            <small>{batch.failed ? 'uszkodzone lub nieobsługiwane' : 'na razie bez błędów'}</small>
            <button className="secondary-button" disabled={batch.failed === 0} onClick={onShowFailed}>Szczegóły</button>
        </section>
    </div>
}

const eventIcon = {named: 'sparkles', failed: 'x'} as const

export function LiveFeed({events, now}: {events: FeedEvent[], now: number}) {
    return <section className="lib-card lib-feed" aria-label="Na żywo">
        <header><h2>Na żywo</h2><small>{events.length ? 'ostatnie wyniki' : 'czekamy na pierwszy wynik'}</small></header>
        <ol>{events.map(event => <li key={`${event.fileId}:${event.at}`} data-kind={event.kind}>
            <span className="lib-feed-icon"><PixelIcon name={eventIcon[event.kind]}/></span>
            <span className="lib-feed-text"><strong>{event.kind === 'named' ? 'Nowa nazwa' : 'Pominięto'}</strong><small>{event.detail}</small></span>
            <time>{Math.max(0, Math.round((now - event.at) / 1000)) < 2 ? 'teraz' : `${Math.round((now - event.at) / 1000)} s`}</time>
        </li>)}</ol>
        <p className="lib-feed-note"><strong>Bez internetu</strong>Analiza działa na tym komputerze. Oryginały nie są zmieniane.</p>
    </section>
}
