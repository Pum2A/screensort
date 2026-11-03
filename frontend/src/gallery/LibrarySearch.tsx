import {useEffect, useId, useRef, useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {PixelIcon} from '../ui/PixelIcon.js'
import {CatalogImage} from './CatalogImage.js'
import {imageUrl} from './catalogApi.js'
import {searchLibrary} from './libraryApi.js'
import type {TextHit} from './libraryApi.js'
import type {LibraryEntry} from './model.js'

type Props = {folderPath: string, entries: LibraryEntry[], query: string, onQuery: (query: string) => void,
    onTextHits: (ids: ReadonlySet<number> | null) => void, onOpen: (fileId: number) => void}

const DELAY_MS = 250

/**
 * Figma v1+ "09 · Stany": one field for names and the text on images. Names match at once in the
 * app; text and what the AI saw come from the backend after a short pause in typing.
 */
export function LibrarySearch({folderPath, entries, query, onQuery, onTextHits, onOpen}: Props) {
    const inputId = useId(), listId = useId()
    const box = useRef<HTMLDivElement>(null)
    const [open, setOpen] = useState(false)
    const [debounced, setDebounced] = useState(query)
    useEffect(() => {
        const timer = window.setTimeout(() => setDebounced(query.trim()), DELAY_MS)
        return () => window.clearTimeout(timer)
    }, [query])
    const text = useQuery({
        queryKey: ['library', folderPath, 'search', debounced],
        queryFn: ({signal}) => searchLibrary(folderPath, debounced, signal),
        enabled: debounced.length >= 2,
        staleTime: 10_000,
    })
    const hits: TextHit[] = debounced.length >= 2 ? text.data?.hits ?? [] : []
    const needle = query.trim().toLocaleLowerCase('pl-PL')
    const nameMatches = needle ? entries.filter(entry => entry.file.originalName.toLocaleLowerCase('pl-PL').includes(needle)) : []
    const names = nameMatches.slice(0, 4)
    const byId = new Map(entries.map(entry => [entry.file.id, entry]))
    const textMatches = hits.filter(hit => byId.has(hit.fileId) && !nameMatches.some(name => name.file.id === hit.fileId))
    const textRows = textMatches.slice(0, 6)
    // The list shows a few; the count says how many the grid will show after Enter.
    const total = nameMatches.length + textMatches.length

    // The grid shows text matches too, so "Enter" and the filter agree with this list.
    useEffect(() => {onTextHits(debounced.length >= 2 && text.data ? new Set(text.data.hits.map(hit => hit.fileId)) : null)},
        [debounced, text.data, onTextHits])
    useEffect(() => {
        function outside(event: PointerEvent) {if (!box.current?.contains(event.target as Node)) setOpen(false)}
        window.addEventListener('pointerdown', outside)
        return () => window.removeEventListener('pointerdown', outside)
    }, [])

    function choose(fileId: number) {setOpen(false); onOpen(fileId)}

    return <div ref={box} className="lib-search-box">
        <label className="lib-search" htmlFor={inputId} data-active={open && needle.length > 0}><PixelIcon name="search"/>
            <span className="sr-only">Szukaj nazwy lub tekstu na obrazie</span>
            <input id={inputId} value={query} placeholder="Szukaj nazwy lub tekstu" role="combobox" aria-expanded={open && total > 0}
                aria-controls={listId} autoComplete="off" onFocus={() => setOpen(true)}
                onChange={event => {onQuery(event.target.value); setOpen(true)}}
                onKeyDown={event => {
                    if (event.key === 'Escape') {setOpen(false); if (!query) event.currentTarget.blur()}
                    else if (event.key === 'Enter') setOpen(false)
                }}/>
            {query && <button type="button" className="lib-search-clear" aria-label="Wyczyść wyszukiwanie" onClick={() => {onQuery(''); setOpen(false)}}><PixelIcon name="x" size={10}/></button>}
        </label>
        {open && needle.length > 0 && <div id={listId} className="lib-search-results" role="listbox" aria-label="Wyniki wyszukiwania">
            <p className="lib-search-head">{text.isFetching ? 'Szukamy w tekście na obrazach…' : `${total} wyników · w nazwach i w tekście na obrazach`}</p>
            {names.map(entry => <button key={`n${entry.file.id}`} type="button" role="option" aria-selected="false" onClick={() => choose(entry.file.id)}>
                <span className="lib-search-thumb"><CatalogImage key={entry.file.id} src={imageUrl(entry.file, 'thumbnail')} name=""/></span>
                <span className="lib-search-text"><strong>{entry.file.originalName}</strong></span><span className="lib-search-tag" data-kind="name">NAZWA</span></button>)}
            {textRows.map(hit => {
                const entry = byId.get(hit.fileId)
                if (!entry) return null
                return <button key={`t${hit.fileId}`} type="button" role="option" aria-selected="false" onClick={() => choose(hit.fileId)}>
                    <span className="lib-search-thumb"><CatalogImage key={entry.file.id} src={imageUrl(entry.file, 'thumbnail')} name=""/></span>
                    <span className="lib-search-text"><strong>{entry.file.originalName}</strong><small>{hit.before}<mark>{hit.match}</mark>{hit.after}</small></span>
                    <span className="lib-search-tag" data-kind={hit.source === 'AI' ? 'ai' : 'text'}>{hit.source === 'AI' ? 'AI' : 'TEKST'}</span></button>
            })}
            {total === 0 && !text.isFetching && <p className="lib-search-empty">Nic nie pasuje do „{query.trim()}”.</p>}
            <p className="lib-search-foot"><kbd>Enter</kbd>pokaż wszystkie w siatce<kbd>Esc</kbd>zamknij</p>
        </div>}
    </div>
}
