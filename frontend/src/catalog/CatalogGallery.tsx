import {useEffect, useId, useRef, useState} from 'react'
import type {ReactNode} from 'react'
import {AnimatePresence} from 'motion/react'
import * as m from 'motion/react-m'
import {Check, FolderTree, Grid2X2, Image, Search, X} from 'lucide-react'
import {CatalogImage} from './CatalogImage'
import {CatalogDetailsPanel} from './CatalogDetailsPanel'
import {ImageDetailsDialog} from './ImageDetailsDialog'
import {imageUrl} from './api'
import {formatSize} from './format'
import type {CatalogFile} from './types'
import {filterCatalogPage, groupCatalogPage} from '../workspace/state'
import type {GalleryLayout} from '../workspace/state'
import styles from './CatalogGallery.module.css'

type Props = {files: CatalogFile[]; children?: ReactNode}
type TileProps = {file: CatalogFile; index: number; selected: boolean; onSelect: () => void; onPreview: (button: HTMLButtonElement) => void}

function ImageTile({file, index, selected, onSelect, onPreview}: TileProps) {
    const src = imageUrl(file, 'thumbnail')
    return <m.button type="button" className={styles.tile} data-selected={selected} aria-pressed={selected}
        aria-label={'Pokaż szczegóły: ' + file.originalName} onClick={onSelect}
        onDoubleClick={event => onPreview(event.currentTarget)} whileHover={{y: -4}} whileTap={{scale: 0.97}}>
        <span className={styles.tileIndex}>{String(index + 1).padStart(2, '0')}</span>
        <div className={styles.preview}><CatalogImage key={src} src={src} name=""/></div>
        <span className={styles.selectionMark} aria-hidden="true">{selected && <Check size={12}/>}</span>
        <div className={styles.info}><strong title={file.originalName}>{file.originalName}</strong>
            <span><i data-format={file.extension.toLowerCase()}/>{file.extension.toUpperCase()}<b>·</b>{formatSize(file.sizeBytes)}</span></div>
    </m.button>
}

export function CatalogGallery({files, children}: Props) {
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const [previewOpen, setPreviewOpen] = useState(false)
    const [query, setQuery] = useState('')
    const [layout, setLayout] = useState<GalleryLayout>('grid')
    const selected = files.find(file => file.id === selectedId)
    const visible = filterCatalogPage(files, query)
    const previewButton = useRef<HTMLButtonElement | null>(null)
    const panel = useRef<HTMLDivElement | null>(null)
    const detailsId = useId(), searchId = useId()

    useEffect(() => {
        if (!selected || !window.matchMedia('(max-width: 900px)').matches) return
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        panel.current?.scrollIntoView({behavior: reducedMotion ? 'instant' : 'smooth', block: 'start'})
    }, [selected])

    function tile(file: CatalogFile, index: number) {
        return <ImageTile key={file.id} file={file} index={index} selected={file.id === selectedId}
            onSelect={() => setSelectedId(file.id)} onPreview={button => {setSelectedId(file.id); previewButton.current = button; setPreviewOpen(true)}}/>
    }

    return <div className={styles.workspace}>
        <div className={styles.catalog}>
            <div className={styles.toolbar}>
                <div className={styles.search}><Search size={15}/><label className="sr-only" htmlFor={searchId}>Szukaj nazwy na tej stronie</label>
                    <input id={searchId} value={query} placeholder="Szukaj na tej stronie…" onChange={event => setQuery(event.target.value)} disabled={!!children}/>
                    {query && <button className={styles.clearSearch} aria-label="Wyczyść wyszukiwanie" onClick={() => setQuery('')}><X size={14}/></button>}
                </div>
                <div className={styles.layoutSwitch} role="group" aria-label="Układ katalogu">
                    <button aria-pressed={layout === 'grid'} aria-label="Siatka obrazów" title="Siatka" onClick={() => setLayout('grid')}><Grid2X2 size={16}/></button>
                    <button aria-pressed={layout === 'branches'} aria-label="Gałęzie według formatu" title="Gałęzie według formatu" onClick={() => setLayout('branches')}><FolderTree size={16}/></button>
                </div>
            </div>
            <div className={styles.canvas}>
                {children ?? (visible.length === 0 ? <div className={styles.noResults}><Search size={25}/><h3>Nie ma takiej nazwy na tej stronie.</h3><button className="text-button" onClick={() => setQuery('')}>Wyczyść wyszukiwanie</button></div>
                    : layout === 'grid' ? <div className={styles.grid} aria-label="Obrazy w katalogu">{visible.map(tile)}</div>
                        : <div className={styles.branches} aria-label="Obrazy pogrupowane według formatu na tej stronie">
                            {groupCatalogPage(visible).map(group => <section key={group.format}><h3><FolderTree size={16}/>{group.format}<span>{group.files.length}</span></h3>
                                <div className={styles.branchGrid}>{group.files.map(tile)}</div></section>)}
                        </div>)}
            </div>
            <div className={styles.canvasFooter}><span>{query ? visible.length + ' wyników na tej stronie' : 'Kliknij obraz, aby rozwinąć jego historię.'}</span><span>Dwuklik → podgląd</span></div>
        </div>
        <div ref={panel} className={styles.sidebar}>
            <p className="sr-only" role="status">{selected ? 'Wybrano ' + selected.originalName + '. Szczegóły obok galerii.' : 'Wybierz obraz, aby zobaczyć szczegóły.'}</p>
            <AnimatePresence mode="wait" initial={false}>
                {selected ? <m.div key={selected.id} initial={{opacity: 0, y: 6}} animate={{opacity: 1, y: 0}} exit={{opacity: 0, y: -4}}>
                    <CatalogDetailsPanel file={selected} onPreview={trigger => {previewButton.current = trigger; setPreviewOpen(true)}}/>
                </m.div> : <m.aside key="empty" className={styles.empty} initial={{opacity: 0}} animate={{opacity: 1}} exit={{opacity: 0}} aria-label="Szczegóły obrazu">
                    <span className={styles.emptyKicker}>INSPEKTOR OBRAZU</span><div className={styles.emptyIllustration}><Image size={36}/><span/><i/></div>
                    <h3>Co kryje ten obraz?</h3><p>Wybierz miniaturę. Tutaj połączymy podgląd, metadane i zapisane wyniki analizy.</p>
                    <div className={styles.emptyTree}><span>Obraz</span><span>Plik i lokalizacja</span><span>Tekst i zawartość</span></div>
                    <small>Oryginał pozostaje bez zmian.</small>
                </m.aside>}
            </AnimatePresence>
        </div>
        <AnimatePresence>{selected && previewOpen && <ImageDetailsDialog key={selected.id} id={detailsId} file={selected}
            trigger={previewButton} onClose={() => setPreviewOpen(false)}/>}</AnimatePresence>
    </div>
}
