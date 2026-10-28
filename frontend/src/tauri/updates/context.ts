import {createContext, useContext, useEffect, useId} from 'react'
import type {UpdateStatus} from './state.js'

export type UpdateContextValue = {status: UpdateStatus; error: string | null; reasons: string[]
    setBlocker: (id: string, reason: string | null) => void
    check: () => void; download: () => void; install: () => void; restart: () => void}
export const AppUpdates = createContext<UpdateContextValue | null>(null)

/** Downloading is allowed during work; installing must wait for work and drafts. */
export function useUpdateBlocker(active: boolean, reason: string) {
    const id = useId()
    const setBlocker = useContext(AppUpdates)?.setBlocker
    useEffect(() => {
        if (!setBlocker) return
        setBlocker(id, active ? reason : null)
        return () => setBlocker(id, null)
    }, [id, active, reason, setBlocker])
}
