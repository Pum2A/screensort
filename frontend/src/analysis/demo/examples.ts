/** Tylko fikcyjne przykłady UI. Nie przypisujemy ich do ID z katalogu PostgreSQL. */
export type AnalysisExample = {
    id: 'person' | 'terminal' | 'document'
    label: string
    originalName: string
    proposedName: string
    description: string
    ocrText: string
    category: string
    tags: readonly string[]
}

export const analysisExamples: readonly AnalysisExample[] = [
    {
        id: 'person', label: 'Osoba w parku', originalName: 'Screenshot_20260917_132604.png',
        proposedName: 'osoba_w_parku.png',
        description: 'Osoba w niebieskim ubraniu na zewnątrz. W tle drzewa; prawdopodobnie park.',
        ocrText: 'Nie wykryto czytelnego tekstu.', category: 'Zdjęcia / osoby',
        tags: ['osoba', 'na zewnątrz', 'drzewa'],
    },
    {
        id: 'terminal', label: 'Błąd PostgreSQL', originalName: 'Screenshot_20260918_091204.png',
        proposedName: 'postgresql_connection_refused.png',
        description: 'Zrzut ekranu z komunikatem o błędzie połączenia z bazą danych.',
        ocrText: 'Connection refused\nlocalhost:5432\nCheck the connection settings.',
        category: 'Kod i błędy', tags: ['PostgreSQL', 'błąd', 'terminal'],
    },
    {
        id: 'document', label: 'Notatki o Javie', originalName: 'IMG_0026.png',
        proposedName: 'java_notatki_kolekcje.png', description: 'Strona z notatkami o kolekcjach w Javie.',
        ocrText: 'Java — kolekcje\nList: uporządkowane elementy\nMap: klucz i wartość',
        category: 'Dokumenty / nauka', tags: ['Java', 'notatki', 'nauka'],
    },
]
