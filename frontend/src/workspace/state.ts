export type WorkspaceView = 'catalog' | 'organization' | 'duplicates'

export function storedFolder(desktop: boolean): string {
    try { return localStorage.getItem('screensort.lastFolder') ?? (desktop ? '' : 'demo-images') }
    catch { return desktop ? '' : 'demo-images' }
}
