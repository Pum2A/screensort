import {PixelIcon} from '../ui/PixelIcon.js'
import type {PixelIconName} from '../ui/pixelIcons.js'
import {diskBytes, downloadBytes, hasAiPackage, installedSummary, packagePercent, packagePhase, packageSize} from './packages/state.js'
import type {PackageSpec, PackageStatus} from './packages/state.js'
import './EngineView.css'

type Runtime = {ready: boolean, starting: boolean, error: string | null}
type Props = {
    status: PackageStatus | null
    /** null in the browser, where the engine runs outside the app. */
    runtime: Runtime | null
    requesting: boolean
    error: string | null
    /** Bytes per second while downloading. */
    speed: number | null
    autoAnalysis: {enabled: boolean, loading: boolean, change: (enabled: boolean) => void}
    onStart: () => void
    onInstall: (id: string, fromFile: boolean) => void
    onCancel: () => void
}

const SEGMENTS = 40
const roles: Record<PackageSpec['kind'], {icon: PixelIconName, role: string}> = {
    qwen: {icon: 'eye', role: 'rozpoznaje aplikację i temat zrzutu, z tego powstaje nazwa i kategoria'},
    python: {icon: 'type', role: 'czyta tekst na obrazie (OCR); bez Qwena także opisuje obraz'},
}

function duration(seconds: number): string {
    if (seconds < 60) return `${Math.max(1, Math.round(seconds))} s`
    const minutes = Math.floor(seconds / 60)
    return minutes < 60 ? `${minutes} min ${Math.round(seconds % 60)} s` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`
}

/** Figma v1+ "08 · Silnik AI": what is installed and running, what each model does, and the settings around it. */
export function EngineView({status, runtime, requesting, error, speed, autoAnalysis, onStart, onInstall, onCancel}: Props) {
    const progress = status?.progress
    const busy = requesting || !!progress?.busy
    const locked = busy || !!runtime?.starting || !!runtime?.ready
    const installed = status ? hasAiPackage(status) : false
    const downloading = progress?.busy ? status?.packages.find(item => !item.installed) : undefined
    const tag = !runtime ? 'PRZEGLĄDARKA' : progress?.busy ? 'POBIERANIE' : runtime.error ? 'WYMAGA UWAGI' : runtime.ready ? 'GOTOWY' : runtime.starting ? 'URUCHAMIAMY' : installed ? 'NA ŻĄDANIE' : 'BRAK MODELU'
    const filled = progress ? Math.round(packagePercent(progress) / 100 * SEGMENTS) : 0
    const left = progress && speed && speed > 0 ? (progress.totalBytes - progress.completedBytes) / speed : null
    const disk = status?.packages.filter(item => item.installed).map(item => ({name: item.package.name.split(' · ')[0], bytes: diskBytes(item.package)})) ?? []
    const diskTotal = disk.reduce((sum, item) => sum + item.bytes, 0)
    // The catalog lists the better Qwen model first.
    const recommended = status?.packages.find(item => item.package.kind === 'qwen')?.package.id

    return <div className="engine-page">
        <div className="engine-main">
            <section className="engine-status" aria-label="Stan silnika">
                <header><span className="engine-status-chip"><PixelIcon name="cpu" size={18}/></span>
                    <div><h2>{progress?.busy ? packagePhase(progress.phase) : status && installed ? installedSummary(status) : 'Wybierz model AI'}</h2>
                        <p>{progress?.busy ? 'Pobieranie wznowi się samo po zerwaniu połączenia. Każdy plik sprawdzamy sumą SHA-256.'
                            : installed ? runtime?.ready ? 'Analiza działa na tym komputerze.' : '„Uporządkuj z AI” uruchomi silnik sam. Możesz też zrobić to teraz.'
                                : 'Pobierz jeden z modeli poniżej. Biblioteka i decyzje działają bez niego.'}</p></div>
                    <span className="engine-tag" data-tone={runtime?.error ? 'warn' : runtime?.ready || progress?.busy ? 'lime' : 'ink'}>{tag}</span></header>
                {progress?.busy && <>
                    <div className="engine-numbers"><strong>{packageSize(progress.completedBytes).replace(' MiB', '')}<span> / {packageSize(progress.totalBytes)}</span></strong>
                        <small>{speed ? `${(speed / 1024 / 1024).toFixed(1).replace('.', ',')} MB/s` : ''}{left ? ` · ok. ${duration(left)}` : ''}</small></div>
                    <div className="engine-segments" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={packagePercent(progress)}
                        aria-label={downloading ? `Pobieranie: ${downloading.package.name}` : 'Instalacja'}>
                        {Array.from({length: SEGMENTS}, (_, index) => <i key={index} data-on={index < filled}/>)}</div>
                    <div className="engine-buttons"><button type="button" className="engine-pill" onClick={onCancel}><PixelIcon name="x" size={10}/>Anuluj</button></div>
                </>}
                {runtime?.starting && <div className="engine-loading" role="status"><span/>Ładujemy model. To trwa chwilę tylko przy pierwszym uruchomieniu po starcie aplikacji.</div>}
                {!progress?.busy && installed && runtime && !runtime.ready && !runtime.starting && <div className="engine-buttons">
                    <button type="button" className="engine-primary" onClick={onStart}><PixelIcon name="sparkles" size={11}/>Uruchom analizę</button></div>}
                {(error ?? runtime?.error ?? progress?.error) && <p className="engine-error" role="alert">{error ?? runtime?.error ?? progress?.error}</p>}
            </section>

            <section className="engine-box" aria-labelledby="engine-models">
                <h2 id="engine-models"><span className="engine-mark">Co robi każdy model</span></h2>
                <ul className="engine-models">
                    {status?.packages.map(({package: pack, installed: ready}) => <li key={pack.id}>
                        <span className="engine-model-icon"><PixelIcon name={roles[pack.kind].icon}/></span>
                        <span className="engine-model-text"><strong>{pack.name.split(' · ')[0]}{pack.id === recommended && <em>zalecany</em>}</strong><small>{roles[pack.kind].role}</small></span>
                        <span className="engine-model-size">{packageSize(downloadBytes(pack))}</span>
                        {ready ? <span className="engine-tag" data-tone="ink">GOTOWY</span>
                            : <span className="engine-model-actions">
                                <button type="button" className="engine-pill" disabled={locked || !pack.url} onClick={() => onInstall(pack.id, false)}><PixelIcon name="download" size={10}/>Pobierz</button>
                                {pack.kind === 'python' && <button type="button" className="engine-link" disabled={locked} onClick={() => onInstall(pack.id, true)}>Mam ZIP</button>}
                            </span>}
                    </li>)}
                    <li><span className="engine-model-icon"><PixelIcon name="tag"/></span>
                        <span className="engine-model-text"><strong>Reguły nazw</strong><small>z aplikacji i tematu robią nazwę pliku, PL lub EN</small></span>
                        <span className="engine-model-size">w aplikacji</span><span className="engine-tag" data-tone="ink">GOTOWY</span></li>
                    <li><span className="engine-model-icon"><PixelIcon name="layers"/></span>
                        <span className="engine-model-text"><strong>Wykrywanie duplikatów</strong><small>porównuje treść plików (SHA-256)</small></span>
                        <span className="engine-model-size">w aplikacji</span><span className="engine-tag" data-tone="ink">GOTOWY</span></li>
                </ul>
                {!status && <p className="engine-muted" role="status">{runtime ? 'Sprawdzamy zainstalowane modele…' : 'W trybie przeglądarkowym modele uruchamiasz osobno.'}</p>}
            </section>
        </div>

        <aside className="engine-side">
            <section className="engine-box" aria-labelledby="engine-analysis">
                <h2 id="engine-analysis">Analiza</h2>
                <div className="engine-setting">
                    <span><strong>Analizuj nowe zdjęcia od razu</strong><small>po każdym wczytaniu folderu</small></span>
                    <button type="button" role="switch" className="engine-switch" aria-checked={autoAnalysis.enabled}
                        aria-label="Analizuj nowe zdjęcia od razu" disabled={autoAnalysis.loading} onClick={() => autoAnalysis.change(!autoAnalysis.enabled)}><i/></button>
                </div>
                <div className="engine-setting">
                    <span><strong>Karta graficzna</strong><small>wybierana sama, gdy jest zgodna (Vulkan)</small></span>
                    <span className="engine-tag" data-tone="ink">AUTO</span>
                </div>
                <p className="engine-muted">Tekst i obraz wybierasz przy analizie folderu w „Do decyzji”.</p>
            </section>
            <section className="engine-privacy" aria-labelledby="engine-privacy">
                <h2 id="engine-privacy"><PixelIcon name="shield"/>Prywatność</h2>
                <p>Obrazy, tekst i nazwy nigdy nie opuszczają tego komputera. Brak kont, chmury i telemetrii treści.</p>
            </section>
            <section className="engine-box" aria-labelledby="engine-disk">
                <h2 id="engine-disk">Miejsce na dysku</h2>
                {disk.length === 0 ? <p className="engine-muted">Żaden model nie jest jeszcze pobrany.</p>
                    : disk.map(item => <div key={item.name} className="engine-disk-row">
                        <span>{item.name}<strong>{packageSize(item.bytes)}</strong></span>
                        <i style={{width: `${Math.max(4, item.bytes / diskTotal * 100)}%`}}/></div>)}
            </section>
        </aside>
    </div>
}
