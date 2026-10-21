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

Pierwszy wariant z ciemnym sidebarem został odrzucony. Zatrzymano budowanie wydania
0.2.4 przed publikacją; dotychczasowy kanał aktualizacji nie został podmieniony.
Kolejny wariant odnosi się do wskazanego przez właściciela
[Revolut FREE UI Kit — Marvilo](https://www.figma.com/community/file/1372290114400007730/revolut-free-ui-kit-by-marvilo).
Obejrzano publiczny podgląd i powiększoną okładkę na stronie Figmy; nie pobierano
plików źródłowych, logo, ilustracji ani fontów z zestawu. To adaptacja stylu, nie
odtworzenie komponentów z dokładnymi pomiarami z Figmy. Zestaw na stronie opisano
jako CC BY 4.0; autor referencji: Marvilo — UI / UX Resources.

Zmiana dotyczy nie tylko gradientu: górna nawigacja zastępuje boczny panel,
aktywna zakładka jest czarną pigułką, przyciski mają spójne kształty, a szybkie
działania — okrągłe pola ikon z podpisami. Duża liczba pokazuje rzeczywisty stan
katalogu, nie wynik analizy ani sztuczną statystykę. Desktop ma kompaktowy układ
licznika i działań, mały ekran układa je pionowo. Galeria ma białą powierzchnię,
delikatne obramowania i wyraźny stan zaznaczenia. Fiolet jest akcentem; treść i
formularze pozostają neutralne. Zielony sukces, bursztynowe ostrzeżenie i czerwony
błąd zachowują odrębne znaczenia. Paginacja, lazy loading i `object-fit: contain`
pozostają bez zmian.

Do review tej korekty: `WorkspaceNavigation.tsx` (zastąpił `ExplorerSidebar.tsx`),
`WorkspaceHeader.tsx`, połączenie akcji w `App.tsx`, `App.css`, tokeny `index.css`,
style galerii, startu i kontrolki aktualizacji. Szybkie działania korzystają z
istniejących funkcji aplikacji — nie dodają nowych endpointów ani logiki modeli.
Ta korekta nadal jest propozycją do weryfikacji, nie zatwierdzonym wydaniem.

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

Koszt bibliotek nie jest zerowy: lokalny build to około 482 kB JS / 154 kB gzip,
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
`kit-workspace.jpg` przedstawia korektę na podstawie referencji, wcześniejsze
`catalog.jpg` i `startup.jpg` dotyczą odrzuconego wariantu.

Dokumentacja użyta przy implementacji: [React Effects](https://react.dev/learn/synchronizing-with-effects),
[Motion accessibility](https://motion.dev/docs/react-accessibility),
[Tauri updater](https://v2.tauri.app/plugin/updater/),
[Radix Tooltip](https://www.radix-ui.com/primitives/docs/components/tooltip).

Nadal nie naprawiono jakości nazw ani startu modeli po błędzie. To osobny etap,
opisany w `current-state.md` i `plan-contextual-analysis.md`; nowy loader nie jest
dowodem szybszego ładowania modelu.
