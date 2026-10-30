import {Check, CheckCircle2, Cpu, Download, FolderArchive, HardDrive, LoaderCircle, Play, ScanEye, ShieldCheck, Type, X} from 'lucide-react'
import {WaitingTime} from '../StartupScreen.js'
import {diskBytes, downloadBytes, hasAiPackage, installedSummary, packagePercent, packagePhase, packageSize} from './state.js'
import type {PackageSpec, PackageStatus} from './state.js'

type Props = {status: PackageStatus | null; starting: boolean; ready: boolean; error: string | null;
    requesting: boolean; onStart: () => void; onInstall: (id: string, fromFile: boolean) => void; onCancel: () => void}

// Pure presentation, also used in the clearly labelled synthetic UI fixture.
export function AnalysisEngineContent({status, starting, ready, error, requesting, onStart, onInstall, onCancel}: Props) {
    const busy = requesting || !!status?.progress.busy
    const disabled = busy || starting || ready
    const cancelled = status?.progress.phase === 'CANCELLED' && !error
    return <section className="analysis-engine-content" aria-label="Silnik analizy obrazów">
        <p className="engine-intro">Odczytaj tekst i sprawdź, co przedstawiają Twoje obrazy. Silnik pobierasz raz, a potem korzystasz z niego lokalnie.</p>
        <div className="engine-capabilities"><span><Type size={18}/>Odczyt tekstu</span><span><Cpu size={18}/>Opis zawartości</span><span><ShieldCheck size={18}/>Bez wysyłania zdjęć</span></div>
        {!status && !error && <p className="inline-loading" role="status"><LoaderCircle size={17} className="spin"/>Sprawdzamy zainstalowane składniki…</p>}
        {status && hasAiPackage(status) && <div className="engine-installed">
            <div><CheckCircle2 size={22}/><span><strong>Silnik jest zainstalowany</strong><small>{installedSummary(status)}</small></span></div>
            <button type="button" className="primary-button" disabled={disabled} onClick={onStart}>
                {starting ? <LoaderCircle size={17} className="spin"/> : ready ? <Check size={17}/> : <Play size={17}/>}
                {starting ? 'Przygotowujemy silnik…' : ready ? 'Analiza gotowa' : 'Uruchom analizę'}
            </button>
        </div>}
        {starting && <div className="engine-warmup" role="status">
            <span className="eyebrow">PRZYGOTOWANIE DO PRACY</span><h3>Ładujemy i sprawdzamy modele.</h3>
            <div className="indeterminate-track" aria-hidden="true"><span/></div><WaitingTime/>
            <p>Silnik nie zgłosił jeszcze gotowości. Możesz zamknąć to okno i wrócić do katalogu — przygotowanie będzie trwało w tle.</p>
        </div>}
        {status?.packages.filter(item => !item.installed && (item.package.kind === 'qwen' || !status.bundled)).map(({package: pack}) =>
            <div key={pack.id} className="engine-download">
                <div className="engine-profile-heading"><span className="profile-symbol">{pack.kind === 'qwen' ? <ScanEye size={24}/> : <Cpu size={24}/>}</span>
                    <div><span className="eyebrow">DOSTĘPNY PROFIL</span><h3>{profileTitle(pack)}</h3></div>
                    <span className="neutral-badge">{pack.kind === 'qwen' ? 'Karta GPU lub CPU' : 'Bez karty GPU'}</span></div>
                <p>{profileDescription(pack)}</p>
                <div className="engine-space"><span><Download size={16}/>Pobranie <strong>{packageSize(downloadBytes(pack))}</strong></span><span><HardDrive size={16}/>Na dysku <strong>{packageSize(diskBytes(pack))}</strong></span></div>
                <div className="button-row"><button type="button" className="primary-button" disabled={disabled || !pack.url} onClick={() => onInstall(pack.id, false)}>
                    {busy ? <LoaderCircle size={17} className="spin"/> : <Download size={17}/>}Pobierz {pack.kind === 'qwen' ? 'model' : 'silnik analizy'}
                </button>{pack.kind === 'python' && <button type="button" className="secondary-button" disabled={disabled} onClick={() => onInstall(pack.id, true)}><FolderArchive size={17}/>Mam już plik ZIP</button>}</div>
                <details className="technical-details"><summary>Wymagania i szczegóły pakietu</summary><p>{profileDetails(pack)}</p></details>
            </div>)}
        {status?.progress.busy && <div className="engine-install-progress" role="status"><div><strong>{packagePhase(status.progress.phase)}</strong><span>{packagePercent(status.progress)}%</span></div>
            <progress max={100} value={packagePercent(status.progress)} aria-label={packagePhase(status.progress.phase)}/>
            <button type="button" className="text-button" onClick={onCancel}><X size={15}/>Anuluj instalację</button>
            <small>Możesz korzystać z katalogu. Przerwane pobieranie rozpocznie się od nowa.</small>
        </div>}
        {(error || status?.progress.error) && <div className={'feedback ' + (cancelled ? 'feedback-neutral' : 'feedback-error')} role={cancelled ? 'status' : 'alert'}>
            <p>{error ?? status?.progress.error}</p></div>}
        {status?.progress.phase === 'INSTALLED' && !ready && !starting && <div className="feedback feedback-success" role="status"><CheckCircle2 size={18}/><p>Składniki gotowe. Teraz możesz uruchomić analizę — bez restartowania katalogu.</p></div>}
    </section>
}

function profileTitle(pack: PackageSpec): string {
    return pack.kind === 'qwen' ? pack.name.replace(' · rozpoznawanie zrzutów', '') + ' · rozpoznaje zrzuty' : 'Analiza lokalna · CPU'
}

function profileDescription(pack: PackageSpec): string {
    return pack.kind === 'qwen'
        ? 'Model rozpoznaje aplikację i temat zrzutu, a z tego powstaje nazwa i kategoria. Tekst nadal odczytuje pakiet OCR, jeśli go masz.'
        : 'Tekst odczytuje RapidOCR, a Florence-2 tworzy opis obrazu. Propozycje wymagają Twojej weryfikacji.'
}

function profileDetails(pack: PackageSpec): string {
    return pack.kind === 'qwen'
        ? `Wersja ${pack.version}. Serwer llama.cpp i model Qwen pobierane są z oficjalnych wydań, każdy plik sprawdzany przez SHA-256. Z kartą graficzną zrzut zajmuje około 2 s, na samym procesorze dłużej. Zdjęcia nie wychodzą z komputera.`
        : `Wersja ${pack.version}. Podczas instalacji potrzebne jest miejsce na pobranie, rozpakowane pliki i 256 MiB zapasu. ZIP musi odpowiadać oficjalnej wersji dla tej aplikacji. Model wykorzystuje AI, ale nie wysyła zdjęć do chmury.`
}
