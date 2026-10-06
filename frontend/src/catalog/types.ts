export type CatalogFile = {
    id: number
    allowedRootId: number
    sourceFolder: string
    relativePath: string
    originalName: string
    extension: string
    sizeBytes: number
    lastModifiedAt: string
    sha256: string
    firstDiscoveredAt: string
    lastSeenAt: string
}

export type CatalogPage = {
    files: CatalogFile[]
    pageNumber: number
    pageSize: number
    totalFiles: number
    hasNext: boolean
}
