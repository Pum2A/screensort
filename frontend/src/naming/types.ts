export type NamingReview = {
    fileId: number
    analysisId: number
    sourceSha256: string
    originalName: string
    proposedName: string | null
    analysisMatchesCatalog: boolean
    approvedName: string | null
    approvedAt: string | null
    approvalMatchesCatalog: boolean
    revision: number
}
