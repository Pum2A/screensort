import {Fragment, useId, useRef, useState} from 'react'
import {CatalogImage} from './CatalogImage'
import {ImageDetailsTree} from './ImageDetailsTree'
import {useGalleryColumns} from './useGalleryColumns'
import {selectedRowEnd} from './galleryLayout'
import {imageUrl} from './api'
import {formatSize} from './format'
import type {CatalogFile} from './types'
import './CatalogGallery.css'

export function CatalogGallery({files}: {files: CatalogFile[]}) {
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const selected = files.find((file) => file.id === selectedId)
    const selectedButton = useRef<HTMLButtonElement | null>(null)
    const detailsId = useId()
    const {grid, columns} = useGalleryColumns()
    const rowEnd = selectedRowEnd(files.findIndex((file) => file.id === selectedId), columns, files.length)
    function closeDetails() {
        setSelectedId(null)
        selectedButton.current?.focus({preventScroll: true})
    }
    return <div className="gallery-layout" onKeyDown={(event) => {
        if (event.key === 'Escape' && selected) {
            event.stopPropagation()
            closeDetails()
        }
    }}>
        <p className="sr-only" role="status">{selected ? `Wybrano ${selected.originalName}. Szczegóły pod rzędem miniatur.` : 'Brak wybranego obrazu.'}</p>
        <div className="gallery-grid" ref={grid} aria-label="Obrazy w katalogu">
            {files.map((file, index) => {
                const src = imageUrl(file, 'thumbnail')
                const isSelected = file.id === selectedId
                return <Fragment key={file.id}><button type="button" className={`image-tile ${isSelected ? 'selected' : ''}`}
                    aria-expanded={isSelected} aria-controls={isSelected ? detailsId : undefined}
                    aria-label={`${isSelected ? 'Zwiń' : 'Pokaż'} szczegóły: ${file.originalName}`}
                    onClick={(event) => {
                        selectedButton.current = event.currentTarget
                        setSelectedId(isSelected ? null : file.id)
                    }}>
                    <div className="tile-preview"><CatalogImage key={src} src={src} name=""/></div>
                    <div className="tile-info"><strong title={file.originalName}>{file.originalName}</strong>
                        <span>{file.extension.toUpperCase()} · {formatSize(file.sizeBytes)}</span>
                    </div>
                </button>
                {index === rowEnd && selected && <ImageDetailsTree key={selected.id} id={detailsId}
                    file={selected} selectedButton={selectedButton} onClose={closeDetails}/>}
                </Fragment>
            })}
        </div>
    </div>
}
