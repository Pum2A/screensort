import {useEffect, useId, useRef} from 'react'
import type {ReactNode, RefObject} from 'react'
import {X} from 'lucide-react'
import * as m from 'motion/react-m'
import {useIsPresent} from 'motion/react'

type Props = {title: string; subtitle?: string; trigger: RefObject<HTMLElement | null>; onClose: () => void; children: ReactNode}

/** AnimatePresence keeps the native modal/focus trap active until its exit completes. */
export function Modal({title, subtitle, trigger, onClose, children}: Props) {
    const element = useRef<HTMLDialogElement>(null)
    const close = useRef<HTMLButtonElement>(null)
    const titleId = useId()
    const present = useIsPresent()
    const outside = useRef(false)
    useEffect(() => {
        const dialog = element.current
        if (!dialog) return
        const restore = trigger.current
        const overflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        dialog.showModal()
        close.current?.focus({preventScroll: true})
        return () => {dialog.close(); document.body.style.overflow = overflow; if (restore?.isConnected) restore.focus({preventScroll: true})}
    }, [trigger])
    return <m.dialog ref={element} className="workspace-modal" aria-labelledby={titleId}
        initial={{opacity: 0, y: 12, scale: 0.98}} animate={{opacity: 1, y: 0, scale: 1}}
        exit={{opacity: 0, y: 8, scale: 0.98}} inert={!present}
        onCancel={event => {event.preventDefault(); onClose()}}
        onPointerDown={event => {const rect = event.currentTarget.getBoundingClientRect(); outside.current = event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom}}
        onPointerCancel={() => {outside.current = false}}
        onClick={event => {const rect = event.currentTarget.getBoundingClientRect(); if (outside.current && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) onClose(); outside.current = false}}
        onKeyDown={event => {
            if (event.key !== 'Tab') return
            const controls = [...event.currentTarget.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), a[href], summary, [tabindex="0"]')]
                .filter(control => control.getClientRects().length > 0 && !control.closest('[inert]'))
            const first = controls[0], last = controls.at(-1)
            if (event.shiftKey && document.activeElement === first && last) {event.preventDefault(); last.focus()}
            else if (!event.shiftKey && document.activeElement === last && first) {event.preventDefault(); first.focus()}
        }}>
        <header className="workspace-modal-heading"><div>{subtitle && <span className="eyebrow">{subtitle}</span>}<h2 id={titleId}>{title}</h2></div>
            <button ref={close} className="icon-button" type="button" aria-label="Zamknij okno" onClick={onClose}><X size={20}/></button></header>
        <div className="workspace-modal-body">{children}</div>
    </m.dialog>
}
