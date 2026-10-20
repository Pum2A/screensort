import type {CatalogFile} from '../catalog/types.js'

export type WorkspaceView = 'catalog' | 'organization'
export type GalleryLayout = 'grid' | 'branches'

export function filterCatalogPage(files: CatalogFile[], query: string): CatalogFile[] {
    const search = query.trim().normalize('NFKC').toLocaleLowerCase('pl-PL')
    return search ? files.filter(file => file.originalName.normalize('NFKC').toLocaleLowerCase('pl-PL').includes(search)) : files
}

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

export function storedFolder(desktop: boolean): string {
    try { return localStorage.getItem('screensort.lastFolder') ?? (desktop ? '' : 'demo-images') }
    catch { return desktop ? '' : 'demo-images' }
}
