import {useEffect, useId, useRef, useState} from 'react'
import type {ReactNode} from 'react'
import {AnimatePresence} from 'motion/react'
import * as m from 'motion/react-m'
import {Check, FolderTree, Grid2X2, Image, Search, X} from 'lucide-react'
import {CatalogImage} from './CatalogImage.js'
import {CatalogDetailsPanel} from './CatalogDetailsPanel.js'
import {ImageDetailsDialog} from './ImageDetailsDialog.js'
import {imageUrl} from './api.js'
import {formatSize} from './format.js'
import type {CatalogFile} from './types.js'
import {filterCatalogPage, groupCatalogPage} from '../workspace/state.js'
import type {GalleryLayout} from '../workspace/state.js'
import {Tooltip} from '../ui/Tooltip.js'
import styles from './CatalogGallery.module.css'

type Props = {files: CatalogFile[]; children?: ReactNode}
type TileProps = {file: CatalogFile; selected: boolean; onSelect: () => void; onPreview: (button: HTMLButtonElement) => void}

function ImageTile({file, selected, onSelect, onPreview}: TileProps) {
    const src = imageUrl(file, 'thumbnail')
    return <m.button type="button" className={styles.tile} data-selected={selected} aria-pressed={selected}
        aria-label={'Pokaż szczegóły: ' + file.originalName} onClick={onSelect}
        onDoubleClick={event => onPreview(event.currentTarget)} whileHover={{y: -3}} whileTap={{scale: 0.985}}>
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

    function tile(file: CatalogFile) {
        return <ImageTile key={file.id} file={file} selected={file.id === selectedId}
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
                    <Tooltip label="Siatka obrazów"><button aria-pressed={layout === 'grid'} aria-label="Siatka obrazów" onClick={() => setLayout('grid')}><Grid2X2 size={17}/></button></Tooltip>
                    <Tooltip label="Pogrupuj według formatu"><button aria-pressed={layout === 'branches'} aria-label="Gałęzie według formatu" onClick={() => setLayout('branches')}><FolderTree size={17}/></button></Tooltip>
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
                    <div className={styles.emptyIllustration}><Image size={24}/></div>
                    <h3>Szczegóły obrazu</h3><p>Wybierz miniaturę, aby zobaczyć podgląd, dane pliku i zapisaną analizę.</p>
                    <small>Oryginał pozostaje bez zmian.</small>
                </m.aside>}
            </AnimatePresence>
        </div>
        <AnimatePresence>{selected && previewOpen && <ImageDetailsDialog key={selected.id} id={detailsId} file={selected}
            trigger={previewButton} onClose={() => setPreviewOpen(false)}/>}</AnimatePresence>
    </div>
}
