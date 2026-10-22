import {z} from 'zod'

// Mirrors ScanSummary in Java; the record rejects negative counters.
export const scanSummarySchema = z.object({
    newImages: z.number().int().nonnegative(),
    rescannedImages: z.number().int().nonnegative(),
    unchangedImages: z.number().int().nonnegative(),
    rejectedFiles: z.number().int().nonnegative(),
})

export const scanStatusSchema = z.enum(['PREPARING', 'SCANNING', 'SAVING', 'COMPLETED', 'FAILED'])

// Mirrors ScanJob in Java. errorMessage is set only for FAILED.
export const scanJobSchema = z.object({
    id: z.string(),
    folderPath: z.string(),
    status: scanStatusSchema,
    summary: scanSummarySchema,
    errorMessage: z.string().nullable(),
})

// Mirrors ScanRejectionsDto. "reason" stays a string so a new RejectionReason gets the fallback label.
export const scanRejectionsSchema = z.object({
    files: z.array(z.object({path: z.string(), originalName: z.string(), reason: z.string()})),
    totalRejected: z.number().int(),
    truncated: z.boolean(),
})

export type ScanSummaryData = z.infer<typeof scanSummarySchema>
export type ScanStatus = z.infer<typeof scanStatusSchema>
export type ScanJob = z.infer<typeof scanJobSchema>
export type ScanRejections = z.infer<typeof scanRejectionsSchema>

export function isFinished(job: ScanJob): boolean {
    return job.status === 'COMPLETED' || job.status === 'FAILED'
}
