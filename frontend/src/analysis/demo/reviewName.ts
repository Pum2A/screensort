/** Wskazówka formularza demo, NIE zgoda na zapis ani pełna walidacja systemu plików. */
export function demoNameError(name: string, originalName: string): string | null {
    const extension = originalName.slice(originalName.lastIndexOf('.'))
    if (!name.trim()) return 'Wpisz propozycję nazwy.'
    if (name.length > 255) return 'Propozycja może mieć najwyżej 255 znaków.'
    if (/[<>:"/\\|?*]/.test(name) || [...name].some(char => char.charCodeAt(0) < 32)) {
        return 'Nazwa nie może zawierać znaków ścieżki ani znaków sterujących.'
    }
    if (/[. ]$/.test(name)) return 'Nazwa nie może kończyć się spacją ani kropką.'
    if (/^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(name)) return 'Ta nazwa jest zarezerwowana w Windows.'
    if (!name.toLowerCase().endsWith(extension.toLowerCase()) || name.length <= extension.length) {
        return `Zachowaj końcówkę ${extension} i wpisz nazwę przed nią.`
    }
    return null
}
