import {useEffect, useId, useRef} from 'react'
import * as m from 'motion/react-m'
import {useIsPresent} from 'motion/react'
import type {RefObject} from 'react'
import {ImageDetailsTree} from './ImageDetailsTree'
import {isOutsideBox} from './galleryLayout'
import type {CatalogFile} from './types'
import {Icon} from '../ui/Icon'
import './ImageDetailsDialog.css'

type Props = {id: string, file: CatalogFile, trigger: RefObject<HTMLButtonElement | null>, onClose: () => void}

export function ImageDetailsDialog({id, file, trigger, onClose}: Props) {
    const dialog = useRef<HTMLDialogElement>(null)
    const closeButton = useRef<HTMLButtonElement>(null)
    const pointerStartedOutside = useRef(false)
    const titleId = useId()
    const hintId = useId()
    const present = useIsPresent()

    useEffect(() => {
        const element = dialog.current
        if (!element) return
        const selectedButton = trigger.current
        const previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        element.showModal()
        closeButton.current?.focus({preventScroll: true})
        return () => {
            element.close()
            document.body.style.overflow = previousOverflow
            // Fokus wraca dopiero po zamknięciu: wcześniej galeria jest nieaktywna.
            if (selectedButton?.isConnected) selectedButton.focus({preventScroll: true})
        }
    }, [trigger])

    return <m.dialog ref={dialog} id={id} className="image-details-dialog" inert={!present}
        initial={{opacity: 0, scale: 0.97, y: 12}} animate={{opacity: 1, scale: 1, y: 0}}
        exit={{opacity: 0, scale: 0.98, y: 8}}
        aria-labelledby={titleId} aria-describedby={hintId}
        onCancel={(event) => {
            event.preventDefault()
            onClose()
        }}
        onKeyDown={(event) => {
            if (event.key !== 'Tab') return
            // Zawijamy końce, żeby Tab nie przechodził z podglądu do paska przeglądarki.
            const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button:not([disabled]), summary'))
                .filter((control) => control.getClientRects().length > 0 && !control.closest('[inert]'))
            const first = controls[0]
            const last = controls.at(-1)
            if (event.shiftKey && event.target === first && last) {
                event.preventDefault()
                last.focus()
            } else if (!event.shiftKey && event.target === last && first) {
                event.preventDefault()
                first.focus()
            }
        }}
        onPointerDown={(event) => {
            pointerStartedOutside.current = isOutsideBox({x: event.clientX, y: event.clientY},
                event.currentTarget.getBoundingClientRect())
        }}
        onPointerCancel={() => { pointerStartedOutside.current = false }}
        onClick={(event) => {
            const outside = isOutsideBox({x: event.clientX, y: event.clientY}, event.currentTarget.getBoundingClientRect())
            if (pointerStartedOutside.current && outside) onClose()
            pointerStartedOutside.current = false
        }}>
        <header className="preview-dialog-heading">
            <div className="preview-dialog-title"><span>PODGLĄD / TYLKO ODCZYT</span>
                <h2 id={titleId} title={file.originalName}>{file.originalName}</h2>
            </div>
            <button ref={closeButton} type="button" className="icon-button" aria-label="Zamknij podgląd" onClick={onClose}>
                <Icon name="close"/>
            </button>
        </header>
        <p id={hintId} className="sr-only">Podgląd obrazu i jego danych. Escape lub krzyżyk zamyka okno.</p>
        <div className="preview-dialog-body"><ImageDetailsTree file={file}/></div>
    </m.dialog>
}
