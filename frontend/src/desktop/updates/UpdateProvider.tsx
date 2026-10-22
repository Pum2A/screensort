import {useCallback, useEffect, useRef, useState} from 'react'
import type {ReactNode} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {isDesktop} from '../transport.js'
import {AppUpdates} from './context.js'
import type {UpdateStatus} from './state.js'
import {installationBlocker, updatePending} from './state.js'

export function UpdateProvider({children}: {children: ReactNode}) {
    const [status, setStatus] = useState<UpdateStatus>({phase: 'CHECKING', currentVersion: '', version: null, notes: null, downloadedBytes: 0, totalBytes: null, error: null})
    const [error, setError] = useState<string | null>(null)
    const [blockers, setBlockers] = useState<Record<string, string>>({})
    const pending = useRef(false)
    const setBlocker = useCallback((id: string, reason: string | null) => {
        setBlockers(current => {
            if (current[id] === (reason ?? undefined)) return current
            const next = {...current}; if (reason) next[id] = reason; else delete next[id]
            return next
        })
    }, [])
    useEffect(() => {
        if (!isDesktop() || !updatePending(status.phase)) return
        let stopped = false
        let timer: ReturnType<typeof setTimeout> | undefined
        async function poll() {
            try {
                // The native process starts the check, independently of React mounting.
                const next = await invoke<UpdateStatus>('app_update_status')
                if (!stopped) setStatus(next)
                if (!updatePending(next.phase)) return
            } catch {
                // A bridge failure must not leave a permanent 'checking' indicator.
                if (!stopped) setStatus(current => ({...current, phase: 'ERROR', error: 'Nie udało się odczytać stanu aktualizacji. Katalog nadal działa.'}))
                return
            }
            if (!stopped) timer = setTimeout(() => void poll(), 750)
        }
        void poll()
        return () => {stopped = true; if (timer !== undefined) clearTimeout(timer)}
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
        {children}
    </AppUpdates.Provider>
}
