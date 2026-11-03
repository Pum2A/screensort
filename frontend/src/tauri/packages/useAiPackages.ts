import {useEffect, useRef, useState} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {watchNative} from '../nativeStatus.js'
import type {PackageStatus} from './state.js'

/** The AI packages the app knows, the running install and its download speed, from the native shell. */
export function useAiPackages() {
    const [status, setStatus] = useState<PackageStatus | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [pollError, setPollError] = useState<string | null>(null)
    const [requesting, setRequesting] = useState(false)
    const [speed, setSpeed] = useState<number | null>(null)
    const sample = useRef<{bytes: number, at: number} | null>(null)
    useEffect(() => watchNative<PackageStatus>('ai_package_status', 'ai-package-status',
        next => {setStatus(next); setPollError(null)},
        () => setPollError('Nie można odczytać stanu silnika. Sprawdź działanie aplikacji.')), [])

    // Bytes per second, smoothed, only while downloading.
    useEffect(() => {
        const progress = status?.progress
        if (!progress || progress.phase !== 'DOWNLOADING') {sample.current = null; setSpeed(null); return}
        const now = Date.now(), previous = sample.current
        sample.current = {bytes: progress.completedBytes, at: now}
        if (!previous || now - previous.at < 500 || progress.completedBytes < previous.bytes) return
        const current = (progress.completedBytes - previous.bytes) / ((now - previous.at) / 1000)
        setSpeed(value => value === null ? current : value * 0.7 + current * 0.3)
    }, [status])

    async function install(id: string, fromFile: boolean) {
        setRequesting(true); setError(null)
        try {await invoke('install_ai_package', {id, fromFile})}
        catch (cause) {setError(typeof cause === 'string' ? cause : 'Nie udało się rozpocząć instalacji.')}
        finally {setRequesting(false)}
    }

    function cancel() {
        void invoke('cancel_ai_package').catch(() => setError('Nie udało się zlecić anulowania.'))
    }

    return {status, error: error ?? pollError, requesting, speed, install, cancel}
}
