import {useEffect, useId, useRef, useState} from 'react'
import type {ReactNode} from 'react'
import {CatalogImage} from './CatalogImage'
import {CatalogDetailsPanel} from './CatalogDetailsPanel'
import {ImageDetailsDialog} from './ImageDetailsDialog'
import {AnalysisDemo} from '../analysis/demo/AnalysisDemo'
import {Icon} from '../ui/Icon'
import {imageUrl} from './api'
import {formatSize} from './format'
import type {CatalogFile} from './types'
import styles from './CatalogGallery.module.css'

type Props = {files: CatalogFile[], demoOpen: boolean, onCloseDemo: () => void, children?: ReactNode}

export function CatalogGallery({files, demoOpen, onCloseDemo, children}: Props) {
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const [previewOpen, setPreviewOpen] = useState(false)
    const selected = files.find((file) => file.id === selectedId)
    const previewButton = useRef<HTMLButtonElement | null>(null)
    const panel = useRef<HTMLDivElement | null>(null)
    const detailsId = useId()

    useEffect(() => {
        if ((!selected && !demoOpen) || !window.matchMedia('(max-width: 900px)').matches) return
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        panel.current?.scrollIntoView({behavior: reducedMotion ? 'instant' : 'smooth', block: 'start'})
    }, [selected, demoOpen])

    return <div className={styles.workspace}>
        <p className="sr-only" role="status">{selected && !demoOpen ? `Wybrano ${selected.originalName}. Szczegóły obok galerii.` : 'Wybierz obraz, aby zobaczyć jego szczegóły.'}</p>
        <div className={styles.catalog}>
        {children ?? <div className={styles.grid} aria-label="Obrazy w katalogu">
            {files.map((file) => {
                const src = imageUrl(file, 'thumbnail')
                const isSelected = file.id === selectedId
                return <button key={file.id} type="button" className={styles.tile} data-selected={isSelected && !demoOpen}
                    aria-pressed={isSelected && !demoOpen}
                    aria-label={`Pokaż szczegóły: ${file.originalName}`}
                    onClick={() => {
                        setSelectedId(file.id)
                        onCloseDemo()
                    }}>
                    <div className={styles.preview}><CatalogImage key={src} src={src} name=""/></div>
                    <div className={styles.info}><strong title={file.originalName}>{file.originalName}</strong>
                        <span>{file.extension.toUpperCase()} · {formatSize(file.sizeBytes)}</span>
                    </div>
                </button>
            })}
        </div>}
        </div>
        <div ref={panel} className={styles.sidebar}>
            {demoOpen ? <AnalysisDemo onClose={onCloseDemo}/>
                : selected ? <CatalogDetailsPanel key={selected.id} file={selected} onPreview={trigger => {
                    previewButton.current = trigger
                    setPreviewOpen(true)
                }}/>
                : <aside className={styles.empty} aria-label="Szczegóły obrazu"><Icon name="image"/>
                    <h3>Każdy obraz ma swoją historię</h3><p>Kliknij kółko w katalogu. Tutaj zobaczysz nazwę, lokalizację i dane z ostatniego skanu.</p>
                    <span>Chcesz zobaczyć kolejny etap? Otwórz „Analiza · demo”.</span></aside>}
        </div>
        {selected && previewOpen && <ImageDetailsDialog key={selected.id} id={detailsId} file={selected}
            trigger={previewButton} onClose={() => setPreviewOpen(false)}/>}
    </div>
}
