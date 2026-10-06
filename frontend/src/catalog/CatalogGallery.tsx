import {useRef, useState} from 'react'
import {CatalogImage} from './CatalogImage'
import {ImageDetailsPanel} from './ImageDetailsPanel'
import {imageUrl} from './api'
import {formatSize} from './format'
import type {CatalogFile} from './types'
import {Icon} from '../ui/Icon'
import './CatalogGallery.css'

export function CatalogGallery({files}: {files: CatalogFile[]}) {
    const [selectedId, setSelectedId] = useState<number | null>(null)
    const selected = files.find((file) => file.id === selectedId)
    const selectedButton = useRef<HTMLButtonElement | null>(null)
    function closeDetails() {
        setSelectedId(null)
        selectedButton.current?.focus({preventScroll: true})
    }
    return <div className={`gallery-layout ${selected ? 'has-selection' : ''}`} onKeyDown={(event) => {
        if (event.key === 'Escape' && selected) {
            event.stopPropagation()
            closeDetails()
        }
    }}>
        <div className="gallery-grid" aria-label="Obrazy w katalogu">
            {files.map((file) => {
                const src = imageUrl(file, 'thumbnail')
                return <button type="button" className={`image-tile ${file.id === selectedId ? 'selected' : ''}`}
                    key={file.id} aria-pressed={file.id === selectedId} aria-label={`Pokaż szczegóły: ${file.originalName}`}
                    onClick={(event) => {
                        selectedButton.current = event.currentTarget
                        setSelectedId(file.id)
                    }}>
                    <div className="tile-preview"><CatalogImage key={src} src={src} name=""/>
                        <span className="tile-selection"><Icon name="check"/></span>
                    </div>
                    <div className="tile-info"><strong title={file.originalName}>{file.originalName}</strong>
                        <span><span className="format-badge">{file.extension.toUpperCase()}</span>{formatSize(file.sizeBytes)}</span>
                    </div>
                </button>
            })}
        </div>
        {selected && <ImageDetailsPanel file={selected} onClose={closeDetails}/>}
    </div>
}
