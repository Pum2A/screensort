export type UpdateStatus = {
    phase: 'IDLE' | 'DEVELOPMENT' | 'CHECKING' | 'AVAILABLE' | 'UP_TO_DATE' | 'DOWNLOADING' | 'DOWNLOADED' | 'INSTALLING' | 'ERROR' | 'RESTART_REQUIRED'
    currentVersion: string; version: string | null; notes: string | null
    downloadedBytes: number; totalBytes: number | null; error: string | null
}
export function updatePercent(status: UpdateStatus): number | undefined {
    return status.totalBytes && status.totalBytes > 0 ? Math.min(100, Math.max(0, status.downloadedBytes / status.totalBytes * 100)) : undefined
}
export function updatePending(phase: UpdateStatus['phase']): boolean {
    return ['IDLE', 'CHECKING', 'DOWNLOADING', 'INSTALLING'].includes(phase)
}
export function updateLabel(status: UpdateStatus): string {
    if (status.phase === 'IDLE' || status.phase === 'CHECKING') return 'Sprawdzamy wersję…'
    if (status.phase === 'DEVELOPMENT') return 'Tryb developerski'
    if (status.phase === 'UP_TO_DATE') return `${status.currentVersion} · aktualna`
    if (status.phase === 'AVAILABLE') return 'Dostępna aktualizacja'
    if (status.phase === 'DOWNLOADING') return 'Pobieramy aktualizację…'
    if (status.phase === 'DOWNLOADED') return 'Aktualizacja gotowa'
    if (status.phase === 'INSTALLING') return 'Instalujemy…'
    if (status.phase === 'RESTART_REQUIRED') return 'Wymagany restart'
    return 'Sprawdź aktualizacje'
}
export function installationBlocker(reasons: readonly string[], status: UpdateStatus): string | null {
    if (reasons.length) return [...new Set(reasons)].join(' ')
    return status.phase !== 'DOWNLOADED' ? 'Najpierw pobierz i zweryfikuj aktualizację.' : null
}
