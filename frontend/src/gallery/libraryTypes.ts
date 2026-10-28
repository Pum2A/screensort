import {z} from 'zod'
import {catalogFileSchema} from './catalogTypes.js'
import {batchItemSchema} from '../organization/types.js'

// Mirrors LibraryReader.Library in Java: every catalog file plus the latest analysis of the folder.
export const librarySchema = z.object({
    files: z.array(catalogFileSchema),
    batchId: z.string().nullable(),
    items: z.array(batchItemSchema),
})

export type Library = z.infer<typeof librarySchema>
