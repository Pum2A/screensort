# ScreenSort 0.2.4 — interfejs i sprawdzanie wersji do przeglądu

Zakres 2026-10-21: prawdziwe komponenty aplikacji i natywny updater. Bez zmian
w Javie, Pythonie, modelach, migracjach i prywatnej bazie użytkownika.

## Kontrola wersji przy każdym uruchomieniu

Wcześniej kontrolę inicjował `useEffect` w `UpdateProvider`, dopiero po zamontowaniu
Reacta. Teraz `setup` Tauri wywołuje `updates::on_launch`. Własne asynchroniczne
zadanie odpytuje podpisany kanał niezależnie od rozruchu bazy/backendu i WebView.
`launch_checked` żyje tylko w pamięci procesu: ponowne otwarcie aplikacji tworzy
nowy stan i nową kontrolę, przeładowanie UI nie dubluje żądania.

```text
Nowy proces Tauri
  ├─ on_launch → check → AVAILABLE / UP_TO_DATE / ERROR
  └─ uruchomienie bazy i backendu → katalog

React → app_update_status → wskaźnik w nagłówku + okno szczegółów
Użytkownik → pobierz → podpis i wersja → instaluj, gdy nic nie pracuje
```

Timeout sprawdzania nadal wynosi 15 sekund. Błąd sieci nie zatrzymuje katalogu
i nie jest pokazywany jako „masz aktualną wersję”. Można ręcznie ponowić kontrolę.
Nie włączono automatycznego pobierania ani restartu bez zgody. Nie zmieniano
klucza, zaufanych adresów, limitu rozmiaru instalatora ani blokad instalacji.
Tryb developerski pozostaje wyłączony z instalowania aktualizacji; wewnętrzne
`--startup-check` nie odpytuje internetu.

Miejsca do review: `frontend/src-tauri/src/updates.rs`, `src/lib.rs`,
`frontend/src/desktop/updates/UpdateProvider.tsx`, `UpdatePrompt.tsx`, `state.ts`.
Polling uwzględnia początkowe IDLE, ignoruje spóźnione odpowiedzi po cleanup i
kończy się przy wyniku lub błędzie. Test Rusta sprawdza osobno kolejną sesję i
ponowną inicjalizację w tej samej sesji; testy frontendu sprawdzają stany/etykiety.
Nie jest to test pobrania i instalacji na prywatnym profilu właściciela.

## Przebudowany UI, nie oddzielny makietowy frontend

`WorkspaceHeader` wydziela nagłówek, rzeczywistą liczbę obrazów i kontrolkę wersji
z `App.tsx`. Nawigacja prowadzi do biblioteki albo analizy/przeglądu. Zachowano
obie zamontowane sekcje — przełączenie nie gubi roboczego formularza.

Zmieniono wspólne tokeny, typografię, odstępy, sidebar, selektor folderu, galerię,
inspektor, analizę, plan eksportu, modale i ekran startu. Główna przestrzeń jest
jasna; ciemna nawigacja, fioletowe zaznaczenie, zielony sukces, bursztynowe
ostrzeżenie i czerwony błąd mają różne role. Miniatury są większymi kartami z
`object-fit: contain`, nie przyciętymi kołami. Paginacja i lazy loading pozostają.

Przejścia nagłówka, inspektora i modali używają istniejącego Motion z `LazyMotion`
i małym `domAnimation`. Hover/press i krótkie wejścia zmieniają głównie transform
i opacity. Nie dodano silnika grafów, animacji wszystkich pozycji w nieskończoność
ani kosztownego rozmycia każdej miniatury. CSS i Motion respektują reduced motion.
Nie deklarujemy pomiaru FPS ani przyspieszenia backendu przez nowy wygląd.

React pozostaje: brak pomiaru wskazującego go jako wąskie gardło. Nowym konkretnym
narzędziem jest `@radix-ui/react-tooltip`: dostępne podpowiedzi z obsługą fokusu,
Escape, opóźnienia i pozycjonowania, zamiast własnej imitacji. Lucide dostarcza
ikony; Tailwind pozostaje dla utilities, a układy mają pliki CSS komponentów.
Przy audycie zależności znaleziono podatności developerskiego Vite 8.3.0;
zaktualizowano do 8.3.3, bez wymuszonego `npm audit fix` i bez zmiany głównej wersji.

Koszt bibliotek nie jest zerowy: lokalny build to około 484 kB JS / 154 kB gzip,
wobec wcześniejszych około 427 kB / 133 kB gzip. Nie ładujemy już fontu retro;
typografia jest systemowa i nie pobiera fontów z sieci. To dane builda, nie benchmark.

## Weryfikacja

- 65/65 testów frontendu, lint, build i typowanie scenariuszy preview; nowy test
  pilnuje kontrastu co najmniej 4.5:1 dla głównego i pomocniczego tekstu na obu
  podstawowych powierzchniach. Nie jest to pełny audyt dostępności wszystkich widoków.
- 23/23 testy Rusta, również pakiety, bridge i ograniczenia updatera.
- `npm audit`: 0 zgłoszonych podatności w zablokowanym zestawie zależności.
- Ręcznie w przeglądarce: wybór obrazu, podgląd, Escape, przełączanie widoków,
  zachowanie roboczej nazwy i okno aktualizacji.
- Szerokość 390 px: zawartość mieści się w obszarze bez poziomego przepełnienia.
- Sprawdzono ekran startu i czerwony komunikat błędu silnika; konsola sprawdzonego
  widoku nie zgłosiła ostrzeżeń ani błędów.

Do ręcznej kontroli: `npm run workspace:preview` w `frontend`, port 5184.
Żółty pasek oznacza dane fikcyjne; stan wersji i obsługa przycisków aktualizacji
w tym scenariuszu to atrapy, bez pobierania i instalacji. Zapis na dysk i kontrola
wersji przez sieć wymagają zainstalowanego desktopu, nie fixture.
Zrzuty z weryfikacji są lokalnie w `.release/ui-fluid-20261021`, poza Gitem.

Dokumentacja użyta przy implementacji: [React Effects](https://react.dev/learn/synchronizing-with-effects),
[Motion accessibility](https://motion.dev/docs/react-accessibility),
[Tauri updater](https://v2.tauri.app/plugin/updater/),
[Radix Tooltip](https://www.radix-ui.com/primitives/docs/components/tooltip).

Nadal nie naprawiono jakości nazw ani startu modeli po błędzie. To osobny etap,
opisany w `current-state.md` i `plan-contextual-analysis.md`; nowy loader nie jest
dowodem szybszego ładowania modelu.
