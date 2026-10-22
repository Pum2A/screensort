import {z} from 'zod'

// Statuses are plain strings in Java, but the database CHECK constraints (V6, V7) fix their values.
export const batchSchema = z.object({
    id: z.string(),
    folderPath: z.string(),
    status: z.enum(['RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED']),
    ocr: z.boolean(),
    visual: z.boolean(),
    total: z.number().int(),
    completed: z.number().int(),
    failed: z.number().int(),
    processing: z.number().int(),
    createdAt: z.string(),
})

export const organizationDecisionSchema = z.object({
    decision: z.enum(['APPROVED', 'SKIPPED']),
    category: z.string().nullable(),
    reviewedAt: z.string(),
    revision: z.number().int(),
    matchesCatalog: z.boolean(),
})

// Mirrors ReviewItem in Java. "category" may be a suggestion key such as REVIEW, not only a final category.
export const batchItemSchema = z.object({
    fileId: z.number().int(),
    originalName: z.string(),
    status: z.enum(['PENDING', 'RUNNING', 'COMPLETED', 'FAILED']),
    category: z.string().nullable(),
    proposedName: z.string().nullable(),
    message: z.string().nullable(),
    analysisId: z.number().int().nullable(),
    sourceSha256: z.string(),
    analysisMatchesCatalog: z.boolean(),
    namingRevision: z.number().int(),
    approvedName: z.string().nullable(),
    review: organizationDecisionSchema.nullable(),
})

export const batchPageSchema = z.object({items: z.array(batchItemSchema), hasNext: z.boolean()})

export const organizationPlanItemSchema = z.object({
    fileId: z.number().int(),
    relativePath: z.string(),
    originalName: z.string(),
    category: z.enum(['PROGRAMMING', 'DOCUMENTS', 'GAMES', 'PHOTOGRAPHS', 'OTHER']),
    approvedName: z.string(),
})

export const organizationPlanPageSchema = z.object({
    files: z.array(organizationPlanItemSchema),
    pageNumber: z.number().int(),
    pageSize: z.number().int(),
    totalFiles: z.number().int(),
    hasNext: z.boolean(),
})

export type Batch = z.infer<typeof batchSchema>
export type OrganizationDecision = z.infer<typeof organizationDecisionSchema>
export type BatchItem = z.infer<typeof batchItemSchema>
export type BatchPage = z.infer<typeof batchPageSchema>
export type OrganizationPlanItem = z.infer<typeof organizationPlanItemSchema>
export type OrganizationPlanPage = z.infer<typeof organizationPlanPageSchema>
