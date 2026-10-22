import {z} from 'zod'

// Mirrors CatalogFileDto in Java. Instants arrive as ISO-8601 strings.
export const catalogFileSchema = z.object({
    id: z.number().int(),
    allowedRootId: z.number().int(),
    sourceFolder: z.string(),
    relativePath: z.string(),
    originalName: z.string(),
    extension: z.string(),
    sizeBytes: z.number().int(),
    lastModifiedAt: z.string(),
    sha256: z.string(),
    firstDiscoveredAt: z.string(),
    lastSeenAt: z.string(),
})

export const catalogPageSchema = z.object({
    files: z.array(catalogFileSchema),
    pageNumber: z.number().int(),
    pageSize: z.number().int(),
    totalFiles: z.number().int(),
    hasNext: z.boolean(),
})

export type CatalogFile = z.infer<typeof catalogFileSchema>
export type CatalogPage = z.infer<typeof catalogPageSchema>
