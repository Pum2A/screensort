export function formatSize(bytes: number): string {
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(2)} GiB`
    if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(2)} MiB`
    if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KiB`
    return `${bytes} B`
}

export function formatDate(value: string): string {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString('pl-PL')
}

export function folderName(path: string): string {
    return path.split(/[\\/]/).filter(Boolean).at(-1) ?? path
}
