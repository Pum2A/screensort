import {useCallback, useEffect, useRef, useState} from 'react'
import type {ReactNode} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {isDesktop} from '../transport'
import {AppUpdates} from './context'
import type {UpdateStatus} from './state'
import {installationBlocker} from './state'
import {UpdatePrompt} from './UpdatePrompt'
import './updates.css'

export function UpdateProvider({children}: {children: ReactNode}) {
    const [status, setStatus] = useState<UpdateStatus>({phase: 'CHECKING', currentVersion: '', version: null, notes: null, downloadedBytes: 0, totalBytes: null, error: null})
    const [error, setError] = useState<string | null>(null)
    const [blockers, setBlockers] = useState<Record<string, string>>({})
    const started = useRef(false)
    const pending = useRef(false)
    const setBlocker = useCallback((id: string, reason: string | null) => {
        setBlockers(current => {
            if (current[id] === (reason ?? undefined)) return current
            const next = {...current}; if (reason) next[id] = reason; else delete next[id]
            return next
        })
    }, [])
    useEffect(() => {
        if (!isDesktop() || started.current) return
        started.current = true
        // Owned by the native state; StrictMode must not launch a second check.
        void invoke<UpdateStatus>('check_app_update').then(setStatus).catch(() => setStatus(current => ({...current, phase: 'ERROR', error: 'Sprawdzanie aktualizacji jest chwilowo niedostępne. Katalog nadal działa.'})))
    }, [])
    useEffect(() => {
        if (!isDesktop() || !['CHECKING', 'DOWNLOADING', 'INSTALLING'].includes(status.phase)) return
        let stopped = false
        let timer: ReturnType<typeof setTimeout>
        async function poll() {
            try {const next = await invoke<UpdateStatus>('app_update_status'); if (!stopped) setStatus(next)} catch { /* The operation's request reports the error. */ }
            if (!stopped) timer = setTimeout(() => void poll(), 750)
        }
        timer = setTimeout(() => void poll(), 750)
        return () => {stopped = true; clearTimeout(timer)}
    }, [status.phase])

    async function operation(command: 'check_app_update' | 'download_app_update' | 'install_app_update' | 'restart_after_update_error') {
        if (pending.current) return
        if (command === 'install_app_update' && installationBlocker(Object.values(blockers), status)) return
        pending.current = true; setError(null)
        setStatus(current => ({...current, phase: command === 'check_app_update' ? 'CHECKING' : command === 'download_app_update' ? 'DOWNLOADING' : command === 'install_app_update' ? 'INSTALLING' : current.phase}))
        try {
            if (command === 'install_app_update' || command === 'restart_after_update_error') await invoke(command)
            else setStatus(await invoke<UpdateStatus>(command))
        } catch (cause) {
            setError(typeof cause === 'string' ? cause : 'Nie udało się wykonać aktualizacji. Spróbuj ponownie.')
            try {setStatus(await invoke<UpdateStatus>('app_update_status'))} catch {setStatus(current => ({...current, phase: 'ERROR'}))}
        }
        finally {pending.current = false}
    }
    if (!isDesktop()) return children
    return <AppUpdates.Provider value={{status, error, reasons: Object.values(blockers), setBlocker,
        check: () => void operation('check_app_update'), download: () => void operation('download_app_update'),
        install: () => void operation('install_app_update'), restart: () => void operation('restart_after_update_error')}}>
        {children}<UpdatePrompt/>
    </AppUpdates.Provider>
}
