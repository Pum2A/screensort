import {useId, useRef, useState} from 'react'
import type {ReactNode} from 'react'
import {Icon} from './Icon'
import styles from './AnimatedBranch.module.css'

type Props = {label: string, children: ReactNode, icon?: ReactNode, defaultOpen?: boolean}

/** Treść pozostaje zamontowana, żeby zamykanie też mogło być animowane. */
export function AnimatedBranch({label, children, icon, defaultOpen = false}: Props) {
    const [open, setOpen] = useState(defaultOpen)
    const id = useId()
    const button = useRef<HTMLButtonElement>(null)
    const content = useRef<HTMLDivElement>(null)

    function toggle() {
        if (open && content.current?.contains(document.activeElement)) button.current?.focus()
        setOpen((current) => !current)
    }

    return <section className={styles.branch}>
        <button ref={button} type="button" id={`${id}-heading`} className={styles.heading}
            aria-expanded={open} aria-controls={`${id}-content`} onClick={toggle}>
            <span className={styles.label}>{icon}{label}</span><Icon name="down" className={styles.chevron}/>
        </button>
        <div ref={content} id={`${id}-content`} className={styles.panel} data-open={open}
            role="region" aria-labelledby={`${id}-heading`} aria-hidden={!open} inert={!open}>
            <div className={styles.clip}><div className={styles.content}>{children}</div></div>
        </div>
    </section>
}
