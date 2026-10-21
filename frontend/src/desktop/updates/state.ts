export type UpdateStatus = {
    phase: 'IDLE' | 'DEVELOPMENT' | 'CHECKING' | 'AVAILABLE' | 'UP_TO_DATE' | 'DOWNLOADING' | 'DOWNLOADED' | 'INSTALLING' | 'ERROR' | 'RESTART_REQUIRED'
    currentVersion: string; version: string | null; notes: string | null
    downloadedBytes: number; totalBytes: number | null; error: string | null
}
export function updatePercent(status: UpdateStatus): number | undefined {
    return status.totalBytes && status.totalBytes > 0 ? Math.min(100, Math.max(0, status.downloadedBytes / status.totalBytes * 100)) : undefined
}
export function installationBlocker(reasons: readonly string[], status: UpdateStatus): string | null {
    if (reasons.length) return [...new Set(reasons)].join(' ')
    return status.phase !== 'DOWNLOADED' ? 'Najpierw pobierz i zweryfikuj aktualizację.' : null
}
