// Reason codes sent by the desktop shell (backend_image in src-tauri/src/desktop/mod.rs).
export const imageFailures = ['missing', 'blocked', 'changed', 'too-large', 'unsupported', 'busy', 'unavailable'] as const
export type ImageFailure = typeof imageFailures[number]

const PREFIX = 'image:'

export function imageFailure(error: unknown): ImageFailure {
    const code = typeof error === 'string' && error.startsWith(PREFIX) ? error.slice(PREFIX.length) : ''
    return imageFailures.find(failure => failure === code) ?? 'unavailable'
}

/** The file itself is the problem, so another attempt would fail the same way. */
export function isPermanent(failure: ImageFailure): boolean {
    return failure === 'missing' || failure === 'blocked' || failure === 'too-large' || failure === 'unsupported'
}

export const imageFailureText: Record<ImageFailure, {label: string, detail: string}> = {
    missing: {label: 'Brak pliku', detail: 'Plik został usunięty albo przeniesiony. Zeskanuj folder ponownie.'},
    blocked: {label: 'Plik zablokowany', detail: 'Plik jest skrótem albo leży poza wybranym folderem, więc go nie otwieramy.'},
    changed: {label: 'Plik się zmienił', detail: 'Plik zmienił się od ostatniego skanu. Zeskanuj folder ponownie.'},
    'too-large': {label: 'Za duży obraz', detail: 'Podgląd obsługuje pliki do 50 MB i do 40 mln pikseli.'},
    unsupported: {label: 'Nieobsługiwany obraz', detail: 'Podgląd obsługuje PNG i JPEG. Plik może być uszkodzony.'},
    busy: {label: 'Podgląd niedostępny', detail: 'Aplikacja jest zajęta. Spróbuj za chwilę.'},
    unavailable: {label: 'Podgląd niedostępny', detail: 'Nie udało się wczytać podglądu.'},
}
