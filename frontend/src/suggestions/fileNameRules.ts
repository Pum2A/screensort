/** User feedback only. Java validates again before any decision is stored. */
export function fileNameProblem(name: string, originalName: string): string | null {
    if (!name.trim()) return 'Wpisz nazwę pliku.'
    if (name.length > 255 || new TextEncoder().encode(name).length > 255) {
        return 'Nazwa może mieć najwyżej 255 znaków i 255 bajtów UTF-8.'
    }
    if (name !== name.trim() || name.endsWith('.')) {
        return 'Usuń spacje z początku i końca oraz kropkę z końca nazwy.'
    }
    if (/[<>:"/\\|?*\p{C}]/u.test(name)) return 'Wpisz samą nazwę, bez ścieżki i znaków niedozwolonych w Windows.'
    const devicePart = name.split('.')[0].trimEnd()
    if (/^(con|prn|aux|nul|clock\$|conin\$|conout\$|com[1-9¹²³]|lpt[1-9¹²³])$/iu.test(devicePart)) {
        return 'Ta nazwa jest zarezerwowana przez Windows.'
    }
    const dot = originalName.lastIndexOf('.')
    const extension = dot > 0 && dot < originalName.length - 1 ? originalName.slice(dot) : ''
    if (!extension || name.length <= extension.length || !name.toLowerCase().endsWith(extension.toLowerCase())
        || /^[. ]+$/.test(name.slice(0, -extension.length))) {
        return 'Zachowaj oryginalne rozszerzenie i wpisz nazwę przed nim.'
    }
    return null
}
