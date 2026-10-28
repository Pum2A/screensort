import {useEffect, useState} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {AnalysisEngineContent} from './AnalysisEngineContent.js'
import type {PackageStatus} from './state.js'
import {watchNative} from '../nativeStatus.js'

export function AiPackagePanel({starting, ready = false, onStart}: {starting: boolean; ready?: boolean; onStart: () => void}) {
    const [status, setStatus] = useState<PackageStatus | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [pollError, setPollError] = useState<string | null>(null)
    const [requesting, setRequesting] = useState(false)
    useEffect(() => watchNative<PackageStatus>('ai_package_status', 'ai-package-status',
        next => {setStatus(next); setPollError(null)},
        () => setPollError('Nie można odczytać stanu silnika. Sprawdź działanie aplikacji.')), [])

    async function install(id: string, fromFile: boolean) {
        setRequesting(true); setError(null)
        try {await invoke('install_ai_package', {id, fromFile})}
        catch (cause) {setError(typeof cause === 'string' ? cause : 'Nie udało się rozpocząć instalacji.')}
        finally {setRequesting(false)}
    }

    return <AnalysisEngineContent status={status} starting={starting} ready={ready} error={error ?? pollError} requesting={requesting}
        onStart={onStart} onInstall={(id, fromFile) => void install(id, fromFile)}
        onCancel={() => void invoke('cancel_ai_package').catch(() => setError('Nie udało się zlecić anulowania.'))}/>
}
