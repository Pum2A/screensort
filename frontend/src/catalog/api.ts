import type {CatalogFile, CatalogPage} from './types'

export async function fetchCatalog(folderPath: string, pageNumber: number, pageSize: number): Promise<CatalogPage> {
    const query = new URLSearchParams({folderPath, pageNumber: pageNumber.toString(), pageSize: pageSize.toString()})
    const response = await fetch(`/api/catalog/page?${query}`)
    if (!response.ok) throw new Error(`Catalog request failed (HTTP ${response.status})`)
    return await response.json() as CatalogPage
}

export function imageUrl(file: CatalogFile, size: 'thumbnail' | 'preview'): string {
    // ID wskazuje rekord. Nie przekazujemy endpointowi ścieżki do otwarcia.
    const version = new URLSearchParams({v: `${file.lastSeenAt}:${file.lastModifiedAt}`})
    return `/api/catalog/files/${file.id}/${size}?${version}`
}
