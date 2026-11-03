import {useEffect, useRef, useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {invoke, isTauri} from '@tauri-apps/api/core'
import {getCurrentWebview} from '@tauri-apps/api/webview'
import * as m from 'motion/react-m'
import {PixelIcon} from '../ui/PixelIcon.js'
import {pixelIconUrl} from '../ui/pixelIcons.js'
import {imagesLabel} from './imagesLabel.js'
import './FirstStart.css'

export type KnownFolder = {kind: 'screenshots' | 'downloads' | 'desktop', path: string, images: number}
const labels: Record<KnownFolder['kind'], string> = {screenshots: 'Zrzuty ekranu', downloads: 'Pobrane', desktop: 'Pulpit'}

/**
 * Figma v1+ "05 · Pierwszy start": the app before any folder. A folder can be dropped on the
 * window, chosen with the picker (Ctrl+O) or picked from the usual Windows places.
 */
export function FirstStart({onChoose, onOpen, error, loadFolders = () => invoke<KnownFolder[]>('known_image_folders')}: {
    onChoose: () => void, onOpen: (folder: string) => void, error: string | null,
    /** Replaced only by the synthetic UI fixture, which has no native app behind it. */
    loadFolders?: () => Promise<KnownFolder[]>}) {
    const [hover, setHover] = useState(false)
    // The listeners below are registered once; they always call the newest handlers.
    const handlers = useRef({onChoose, onOpen})
    useEffect(() => {handlers.current = {onChoose, onOpen}})
    const folders = useQuery({queryKey: ['known-folders'], queryFn: loadFolders, staleTime: 60_000})
    const screenshots = folders.data?.find(folder => folder.kind === 'screenshots')
    const found = folders.data?.filter(folder => folder.images > 0) ?? []

    // Tauri hands over the dropped paths; the folder read rejects anything that is not a folder.
    useEffect(() => {
        if (!isTauri()) return
        let stop: (() => void) | null = null, active = true
        void getCurrentWebview().onDragDropEvent(event => {
            if (event.payload.type === 'over' || event.payload.type === 'enter') setHover(true)
            else if (event.payload.type === 'leave') setHover(false)
            else if (event.payload.type === 'drop') {setHover(false); if (event.payload.paths[0]) handlers.current.onOpen(event.payload.paths[0])}
        }).then(unlisten => {if (active) stop = unlisten; else unlisten()}, () => undefined)
        return () => {active = false; stop?.()}
    }, [])

    useEffect(() => {
        function onKey(event: KeyboardEvent) {
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'o') {event.preventDefault(); handlers.current.onChoose()}
        }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [])

    return <main className="first-start">
        <m.div className="first-start-center" initial={{opacity: 0, y: 12}} animate={{opacity: 1, y: 0}}>
            <img className="first-start-mark" src={pixelIconUrl('logo-mark')} width={72} height={72} alt=""/>
            <h1>Uporządkujmy Twoje zrzuty ekranu</h1>
            <p className="first-start-lead">Wybierz folder. ScreenSort rozpozna, co jest na obrazach, zaproponuje nazwy i kategorie,
                a Ty tylko zatwierdzasz. Wszystko dzieje się na tym komputerze.</p>
            <section className="first-start-drop" data-hover={hover} aria-label="Wybór folderu">
                <span className="first-start-icon"><PixelIcon name="folder" size={22}/></span>
                <strong>{hover ? 'Puść, a wczytamy ten folder' : 'Upuść folder tutaj'}</strong>
                <span className="first-start-or">albo</span>
                <div className="first-start-buttons">
                    <button type="button" className="first-start-primary" onClick={onChoose}><PixelIcon name="folder"/>Wybierz folder…<kbd>Ctrl O</kbd></button>
                    {screenshots && <button type="button" className="first-start-secondary" onClick={() => onOpen(screenshots.path)}>Użyj „Zrzuty ekranu”</button>}
                </div>
            </section>
            {error && <p className="first-start-error" role="alert">{error}</p>}
            {found.length > 0 && <section className="first-start-found" aria-labelledby="first-start-found">
                <h2 id="first-start-found">Znalezione foldery z obrazami</h2>
                <div>{found.map(folder => <button key={folder.kind} type="button" onClick={() => onOpen(folder.path)} title={folder.path}>
                    <span className="first-start-mosaic" aria-hidden="true"><i/><i/><i/><i/></span>
                    <span className="first-start-folder"><strong>{labels[folder.kind]}</strong><small>{imagesLabel(folder.images)}{folder.images >= 10_000 ? '+' : ''}</small></span>
                    <PixelIcon name="chevron-right" size={10}/>
                </button>)}</div>
            </section>}
            <ul className="first-start-trust">
                <li><PixelIcon name="lock"/>Bez chmury i kont</li>
                <li><PixelIcon name="shield"/>Oryginały nietknięte</li>
                <li><PixelIcon name="cpu"/>AI działa lokalnie</li>
            </ul>
        </m.div>
    </main>
}
