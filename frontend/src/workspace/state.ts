import type {CatalogFile} from '../catalog/types.js'

export type WorkspaceView = 'catalog' | 'organization'
export type GalleryLayout = 'grid' | 'branches'

export function groupCatalogPage(files: CatalogFile[]): {format: string; files: CatalogFile[]}[] {
    const groups = new Map<string, CatalogFile[]>()
    for (const file of files) {
        const extension = file.extension.toLowerCase()
        const format = extension === 'jpeg' || extension === 'jpg' ? 'JPG / JPEG' : extension.toUpperCase()
        const group = groups.get(format) ?? []
        group.push(file)
        groups.set(format, group)
    }
    return [...groups].map(([format, files]) => ({format, files}))
}

export function storedFolder(): string {
    // No default folder: the user picks one, there is no bundled demo folder anymore.
    try { return localStorage.getItem('screensort.lastFolder') ?? '' }
    catch { return '' }
}
