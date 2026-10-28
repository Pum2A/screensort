export type PackageSpec = {
    id: string; name: string; version: string; archiveBytes: number; unpackedBytes: number; url: string | null
}
export type PackageStatus = {
    bundled: boolean
    packages: {package: PackageSpec; installed: boolean}[]
    progress: {phase: string; busy: boolean; completedBytes: number; totalBytes: number; error: string | null}
}

export function packagePercent(progress: PackageStatus['progress']): number {
    if (progress.totalBytes <= 0) return 0
    return Math.min(100, Math.max(0, Math.floor(progress.completedBytes / progress.totalBytes * 100)))
}

export function packagePhase(phase: string): string {
    return ({PREPARING: 'Przygotowywanie instalacji', DOWNLOADING: 'Pobieranie i kontrola SHA-256',
        VERIFYING: 'Sprawdzanie wybranego ZIP', EXTRACTING: 'Instalowanie składników analizy', ACTIVATING: 'Kończenie instalacji',
        INSTALLED: 'Pakiet jest gotowy do uruchomienia', CANCELLED: 'Instalacja anulowana',
        FAILED: 'Nie udało się zainstalować pakietu'} as Record<string, string>)[phase] ?? 'Silnik analizy'
}

export function packageSize(bytes: number): string {
    return `${(bytes / 1024 / 1024).toFixed(0)} MiB`
}

export function hasAiPackage(status: PackageStatus): boolean {
    return status.bundled || status.packages.some(item => item.installed)
}
