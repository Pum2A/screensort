import {useEffect, useRef, useState} from 'react'
import {PixelIcon} from '../ui/PixelIcon.js'
import type {PixelIconName} from '../ui/pixelIcons.js'
import {categoryLabels} from '../review/state.js'
import type {ImageCategory} from '../review/state.js'
import {categoryIcons} from './categoryIcons.js'
import {categories, isPending} from './model.js'
import type {LibraryEntry} from './model.js'

export type TileAction = {kind: 'open' | 'approve' | 'rename' | 'copy-text' | 'skip'} | {kind: 'category', category: ImageCategory}

type Props = {entry: LibraryEntry, x: number, y: number, canDecide: boolean, onAction: (action: TileAction) => void, onClose: () => void}

/** Figma v1+ "09 · Stany": the right-click menu of one tile, with a category submenu. */
export function TileMenu({entry, x, y, canDecide, onAction, onClose}: Props) {
    const menu = useRef<HTMLDivElement>(null)
    const [submenu, setSubmenu] = useState(false)
    const decidable = canDecide && (isPending(entry.state) || entry.state === 'approved' || entry.state === 'skipped')
    const approvable = canDecide && isPending(entry.state) && !!entry.category && !!entry.item?.proposedName && !entry.item.proposalUncertain

    // Opens where the pointer was, but never off screen; closes on Escape or any click outside.
    const [position, setPosition] = useState({left: x, top: y})
    useEffect(() => {
        const box = menu.current?.getBoundingClientRect()
        if (box) setPosition({left: Math.min(x, window.innerWidth - box.width - 8), top: Math.min(y, window.innerHeight - box.height - 8)})
        menu.current?.querySelector<HTMLButtonElement>('button:not([disabled])')?.focus()
    }, [x, y])
    useEffect(() => {
        function outside(event: PointerEvent) {if (!menu.current?.contains(event.target as Node)) onClose()}
        function key(event: KeyboardEvent) {if (event.key === 'Escape') {event.preventDefault(); onClose()}}
        window.addEventListener('pointerdown', outside)
        window.addEventListener('keydown', key)
        return () => {window.removeEventListener('pointerdown', outside); window.removeEventListener('keydown', key)}
    }, [onClose])

    function item(icon: PixelIconName, label: string, kind: Exclude<TileAction['kind'], 'category'>, shortcut?: string, disabled = false) {
        return <button type="button" role="menuitem" disabled={disabled} onClick={() => onAction({kind})}>
            <PixelIcon name={icon}/>{label}{shortcut && <kbd>{shortcut}</kbd>}</button>
    }

    return <div ref={menu} className="tile-menu" role="menu" aria-label={`Akcje: ${entry.file.originalName}`} style={position}
        onKeyDown={event => {
            if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
            event.preventDefault()
            const buttons = [...(menu.current?.querySelectorAll<HTMLButtonElement>(':scope > button:not([disabled])') ?? [])]
            const index = buttons.indexOf(document.activeElement as HTMLButtonElement)
            buttons[(index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length]?.focus()
        }}>
        {item('eye', 'Podgląd', 'open', 'Spacja')}
        {item('check', 'Zatwierdź nazwę', 'approve', 'Enter', !approvable)}
        {item('pencil', 'Zmień nazwę', 'rename', 'E', !decidable)}
        <button type="button" role="menuitem" aria-haspopup="menu" aria-expanded={submenu} disabled={!decidable}
            onClick={() => setSubmenu(true)} onMouseEnter={() => decidable && setSubmenu(true)}>
            <PixelIcon name="tag"/>Zmień kategorię<PixelIcon name="chevron-right" size={10}/></button>
        {submenu && <div className="tile-submenu" role="menu" aria-label="Kategoria">{categories.map((category, index) =>
            <button key={category} type="button" role="menuitemradio" aria-checked={entry.category === category}
                onClick={() => onAction({kind: 'category', category})}>
                <PixelIcon name={categoryIcons[category]}/>{categoryLabels[category]}<kbd>{index + 1}</kbd></button>)}</div>}
        <span className="tile-menu-line" role="separator"/>
        {item('copy', 'Kopiuj tekst z obrazu', 'copy-text', 'Ctrl C', !entry.item?.analysisId)}
        {item('x', 'Wyklucz z kopii', 'skip', 'S', !decidable || entry.state === 'skipped')}
    </div>
}

/** "Zatwierdzono 3 nazwy · Gry" with Cofnij (Ctrl+Z) and a bar that runs out with the time to undo. */
export function UndoToast({message, busy, onUndo, onDismiss}: {message: string, busy: boolean, onUndo: () => void, onDismiss: () => void}) {
    useEffect(() => {
        const timer = window.setTimeout(onDismiss, 8000)
        function key(event: KeyboardEvent) {
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z' && !(event.target instanceof HTMLInputElement)) {
                event.preventDefault(); onUndo()
            }
        }
        window.addEventListener('keydown', key)
        return () => {window.clearTimeout(timer); window.removeEventListener('keydown', key)}
    }, [onUndo, onDismiss])
    return <div className="undo-toast" role="status">
        <span className="undo-toast-check"><PixelIcon name="check"/></span>
        <span>{message}</span>
        <button type="button" disabled={busy} onClick={onUndo}><PixelIcon name="undo" size={11}/>{busy ? 'Cofamy…' : 'Cofnij'}<kbd>Ctrl Z</kbd></button>
        <i aria-hidden="true"/>
    </div>
}
