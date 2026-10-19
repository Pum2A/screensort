import {useEffect, useState} from 'react'
import {invoke} from '@tauri-apps/api/core'
import {Download, FolderArchive, LoaderCircle, PackageCheck, Play, X} from 'lucide-react'
import {hasAiPackage, packagePercent, packagePhase, packageSize} from './state'
import type {PackageStatus} from './state'

export function AiPackagePanel({starting, onStart}: {starting: boolean; onStart: () => void}) {
    const [status, setStatus] = useState<PackageStatus | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [requesting, setRequesting] = useState(false)
    useEffect(() => {
        let stopped = false
        let timer: ReturnType<typeof setTimeout>
        async function check() {
            try {
                const next = await invoke<PackageStatus>('ai_package_status')
                if (!stopped) setStatus(next)
            } catch {
                if (!stopped) setError('Nie można odczytać pakietów AI. Spróbuj ponownie uruchomić aplikację.')
            }
            if (!stopped) timer = setTimeout(() => void check(), 1000)
        }
        void check()
        return () => { stopped = true; clearTimeout(timer) }
    }, [])

    async function install(id: string, fromFile: boolean) {
        setRequesting(true); setError(null)
        try { await invoke('install_ai_package', {id, fromFile}) }
        catch (cause) { setError(typeof cause === 'string' ? cause : 'Nie udało się rozpocząć instalacji.') }
        finally { setRequesting(false) }
    }
    const busy = requesting || !!status?.progress.busy
    const disabled = busy || starting
    return <section className="mx-auto my-4 max-w-4xl rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-label="Pakiet AI">
        <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3"><PackageCheck aria-hidden="true" size={24}/><div>
                <h2 className="text-lg font-semibold">AI wtedy, gdy go potrzebujesz</h2>
                <p className="text-sm text-slate-600">Katalog działa bez modeli. Analiza pozostaje na Twoim komputerze.</p>
            </div></div>
            {status && hasAiPackage(status) && <button type="button" className="primary-button" disabled={disabled} onClick={onStart}>
                {starting ? <LoaderCircle className="spin" size={16}/> : <Play size={16}/>}
                {starting ? 'Ładowanie modeli…' : 'Uruchom AI'}
            </button>}
        </div>
        {status?.packages.filter(item => !item.installed && !status.bundled).map(({package: pack}) => <div key={pack.id} className="mt-4 rounded-xl bg-slate-50 p-4">
            <h3 className="font-semibold">{pack.name}</h3>
            <p className="mt-1 text-sm text-slate-600">RapidOCR + Florence-2 · CPU · wersja {pack.version}</p>
            <p className="mt-1 text-sm text-slate-600">Pobranie: {packageSize(pack.archiveBytes)}. Pliki AI: {packageSize(pack.unpackedBytes)}.
                {' '}Podczas instalacji potrzebne jest miejsce na oba oraz 256 MiB zapasu.</p>
            <div className="mt-3 flex flex-wrap gap-2">
                <button type="button" className="primary-button" disabled={disabled || !pack.url} onClick={() => void install(pack.id, false)}>
                    <Download size={16}/> Pobierz pakiet AI
                </button>
                <button type="button" className="secondary-button" disabled={disabled} onClick={() => void install(pack.id, true)}>
                    <FolderArchive size={16}/> Wybierz pobrany ZIP
                </button>
            </div>
            <p className="mt-2 text-xs text-slate-500">ZIP musi być oficjalną wersją dla tej aplikacji. Nie instalujemy przypadkowych pakietów pip.</p>
        </div>)}
        {status?.progress.busy && <div className="mt-4" role="status">
            <div className="mb-2 flex items-center justify-between gap-3"><span>{packagePhase(status.progress.phase)} · {packagePercent(status.progress)}%</span>
                <button type="button" className="secondary-button" onClick={() => void invoke('cancel_ai_package').catch(() => setError('Nie udało się zlecić anulowania.'))}>
                    <X size={16}/> Anuluj
                </button>
            </div>
            <progress className="h-2 w-full accent-blue-600" aria-label={packagePhase(status.progress.phase)} max={100} value={packagePercent(status.progress)}/>
            <p className="mt-1 text-xs text-slate-500">Katalog nadal działa. Przerwane pobieranie można rozpocząć ponownie; nie jest jeszcze wznawiane od ostatniego bajtu.</p>
        </div>}
        {status?.progress.phase === 'INSTALLED' && <p className="mt-3 text-sm text-emerald-700" role="status">Pakiet zainstalowany. Kliknij „Uruchom AI” — nie trzeba restartować katalogu.</p>}
        {(error || status?.progress.error) && <p className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">{error ?? status?.progress.error}</p>}
    </section>
}
