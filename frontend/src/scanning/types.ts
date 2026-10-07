export type ScanSummaryData = {
    newImages: number
    rescannedImages: number
    unchangedImages: number
    rejectedFiles: number
}

export type ScanStatus = 'PREPARING' | 'SCANNING' | 'SAVING' | 'COMPLETED' | 'FAILED'

export type ScanJob = {
    id: string
    folderPath: string
    status: ScanStatus
    summary: ScanSummaryData
    errorMessage: string | null
}

export type ScanRejections = {
    files: {path: string, originalName: string, reason: string}[]
    totalRejected: number
    truncated: boolean
}

export function isFinished(job: ScanJob): boolean {
    return job.status === 'COMPLETED' || job.status === 'FAILED'
}
