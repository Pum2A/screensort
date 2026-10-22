import {z} from 'zod'

const failureSchema = z.object({code: z.string(), message: z.string()})

// Mirrors AnalysisResult.validateStep in Java: a completed step has a result, a failed one has an error, a skipped one has neither.
function stepSchema<T extends z.ZodType>(result: T) {
    return z.discriminatedUnion('status', [
        z.object({status: z.literal('COMPLETED'), result, error: z.null().optional()}),
        z.object({status: z.literal('FAILED'), result: z.null().optional(), error: failureSchema}),
        z.object({status: z.literal('SKIPPED'), result: z.null().optional(), error: z.null().optional()}),
    ])
}

const ocrResultSchema = z.object({
    text: z.string(), engine: z.string(), engine_version: z.string(), duration_ms: z.number(),
})
const visualResultSchema = z.object({
    description: z.string(), model: z.string(), model_revision: z.string(),
    task: z.string(), device: z.string(), duration_ms: z.number(),
})

export const savedAnalysisSchema = z.object({
    id: z.number().int(),
    fileId: z.number().int(),
    analyzedAt: z.string(),
    matchesCatalog: z.boolean(),
    result: z.object({
        source_sha256: z.string(),
        ocr: stepSchema(ocrResultSchema),
        visual: stepSchema(visualResultSchema),
    }),
})

export const analysisJobSchema = z.object({
    id: z.string(),
    fileId: z.number().int(),
    status: z.enum(['PREPARING', 'ANALYZING', 'SAVING', 'COMPLETED', 'FAILED']),
    startedAt: z.string(),
    analysis: savedAnalysisSchema.nullable(),
    errorMessage: z.string().nullable(),
})

export type OcrResult = z.infer<typeof ocrResultSchema>
export type VisualResult = z.infer<typeof visualResultSchema>
export type SavedAnalysis = z.infer<typeof savedAnalysisSchema>
export type AnalysisJob = z.infer<typeof analysisJobSchema>

export function isFinished(job: AnalysisJob): boolean {
    return job.status === 'COMPLETED' || job.status === 'FAILED'
}
