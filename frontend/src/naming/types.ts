import {z} from 'zod'

// The schema is the single source of truth: the type is derived from it, so both cannot drift apart.
export const namingReviewSchema = z.object({
    fileId: z.number().int(),
    analysisId: z.number().int(),
    sourceSha256: z.string(),
    originalName: z.string(),
    proposedName: z.string().nullable(),
    analysisMatchesCatalog: z.boolean(),
    approvedName: z.string().nullable(),
    approvedAt: z.string().nullable(),
    approvalMatchesCatalog: z.boolean(),
    revision: z.number().int(),
})

export type NamingReview = z.infer<typeof namingReviewSchema>
