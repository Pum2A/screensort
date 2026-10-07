import {useId, useRef, useState} from 'react'
import {CatalogImage} from './CatalogImage'
import {ImageDetailsDialog} from './ImageDetailsDialog'
import {imageUrl} from './api'
import {formatSize} from './format'
import type {CatalogFile} from './types'
import './CatalogGallery.css'

export function CatalogGallery({files}: {files: CatalogFile[]}) {
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const selected = files.find((file) => file.id === selectedId)
    const selectedButton = useRef<HTMLButtonElement | null>(null)
    const detailsId = useId()
    function closeDetails() {
        setSelectedId(null)
    }
    return <div className="gallery-layout">
        <p className="sr-only" role="status">{selected ? `Wybrano ${selected.originalName}. Otwarty podgląd.` : 'Brak wybranego obrazu.'}</p>
        <div className="gallery-grid" aria-label="Obrazy w katalogu">
            {files.map((file) => {
                const src = imageUrl(file, 'thumbnail')
                const isSelected = file.id === selectedId
                return <button key={file.id} type="button" className={`image-tile ${isSelected ? 'selected' : ''}`}
                    aria-haspopup="dialog" aria-controls={isSelected ? detailsId : undefined}
                    aria-label={`Pokaż podgląd: ${file.originalName}`}
                    onClick={(event) => {
                        selectedButton.current = event.currentTarget
                        setSelectedId(file.id)
                    }}>
                    <div className="tile-preview"><CatalogImage key={src} src={src} name=""/></div>
                    <div className="tile-info"><strong title={file.originalName}>{file.originalName}</strong>
                        <span>{file.extension.toUpperCase()} · {formatSize(file.sizeBytes)}</span>
                    </div>
                </button>
            })}
        </div>
        {selected && <ImageDetailsDialog key={selected.id} id={detailsId} file={selected}
            trigger={selectedButton} onClose={closeDetails}/>}
    </div>
}
