const labels: Record<string, string> = {
    UNSUPPORTED_EXTENSION: 'Nieobsługiwany format pliku',
    NOT_A_REGULAR_FILE: 'To katalog lub inny wpis, nie zwykły plik',
    SYMBOLIC_LINK: 'Pominięto skrót lub dowiązanie dla bezpieczeństwa',
    TOO_LARGE: 'Plik przekracza limit rozmiaru',
    UNREADABLE_IMAGE: 'Nie udało się odczytać obrazu',
    METADATA_UNREADABLE: 'Nie udało się odczytać informacji o pliku',
}
export function rejectionLabel(code: string): string {
    return labels[code] ?? 'Nie udało się sprawdzić tego wpisu'
}
