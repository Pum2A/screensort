# ScreenSort — mapa obecnego projektu

Ten dokument opisuje kod, który **już istnieje**. To nie jest lista pakietów do
utworzenia na przyszłość. Rdzeń to jedna aplikacja Spring Boot z frontendem
React/Tauri. Obok jest niezależny lokalny szkielet serwisu analizy Python;
nie rozbijamy całego produktu na zbiór mikroserwisów.

Ostatnia aktualizacja: 2026-10-11.

## Gdzie czego szukać

```text
screenSorter/
├── analysis-service/              osobny proces Python; obecnie tylko GET /health
│   ├── pyproject.toml             pakiet, zależności i narzędzia
│   ├── uv.lock                    dokładne wersje zależności
│   ├── src/screensort_analysis/
│   │   ├── main.py                tworzy aplikację HTTP
│   │   └── api/                   health.py i schemas.py: routing + JSON
│   └── tests/test_health.py       testy bez modeli, obrazów i bazy
├── frontend/src/                 React: ekran, stan i wywołania /api
│   ├── App.tsx                   skan, wynik i stronicowany katalog
│   ├── scanning/                 zadanie skanu, polling, etapy i liczniki
│   │   ├── types.ts             kształt JSON zadań i podsumowania
│   │   ├── api.ts               POST startu oraz GET statusu/powodów
│   │   ├── polling.ts           sekwencyjne odpytywanie tego samego ID
│   │   ├── useScanJob.ts         stan Reacta i obsługa błędów/cleanup
│   │   ├── ScanProgress.tsx     etapy i liczba sprawdzonych wpisów
│   │   └── ScanSummary.tsx      cztery liczniki zadania
│   ├── catalog/                  kółka, drzewko szczegółów, typy i API
│   │   ├── CatalogGallery.tsx    wybór zdjęcia w siatce kółek
│   │   ├── CatalogDetailsPanel.tsx szczegóły wybranego pliku obok galerii
│   │   ├── ImageDetailsDialog.tsx powiększenie: centralny podgląd, tło, X i klawiatura
│   │   ├── ImageDetailsTree.tsx  podgląd + Plik / Lokalizacja / Historia / Techniczne
│   │   ├── CatalogImage.tsx      ładowanie i błąd obrazu
│   │   ├── galleryLayout.ts      obliczenia linii i kliknięcia poza oknem
│   │   └── useTreeConnections.ts dopasowanie linii do wymiarów węzłów
│   ├── analysis/demo/            osobne fikcyjne przykłady, bez backendu analizy
│   │   ├── AnalysisDemo.tsx      opis wizualny, tekst, kategoria, tagi
│   │   ├── examples.ts          dane trzech przykładów, niezwiązane z katalogiem
│   │   ├── ReviewActions.tsx    formularz i zatwierdzanie tylko w stanie Reacta
│   │   └── reviewName.ts        wskazówki dla propozycji nazwy w demo
│   ├── assets/fonts/             lokalny font retro VT323
│   └── ui/                      wspólne elementy widoku
│       ├── Icon.tsx             ikony SVG
│       └── AnimatedBranch.tsx   płynnie rozwijana i zwijana gałąź
├── frontend/public/VT323-OFL.txt  licencja fontu dołączana do dystrybucji
├── frontend/tests/               testy układu, kontraktu API, pollingu i danych/formularza demo
├── src/main/java/pl/pum/screensort/
│   ├── ScreenSortApplication.java  uruchamia Spring Boot
│   ├── scanning/                   funkcja: sprawdź folder i przeskanuj obrazy
│   │   ├── workflow/               kolejność kroków całego skanowania
│   │   │   ├── ScanWorkflow.java   walidacja → znane pliki → skan → zapis
│   │   │   └── ScanProgressListener.java  powiadomienie o etapie i licznikach
│   │   ├── jobs/                   opis i obsługa uruchomienia skanu
│   │   │   ├── ScanStatus.java     enum etapów całego zadania
│   │   │   ├── ScanJob.java        record: ID, folder, status, liczniki, błąd
│   │   │   ├── ScanJobManager.java start, aktualizacja i odczyt stanu
│   │   │   └── ScanAlreadyRunningException.java  drugi skan jest blokowany
│   │   ├── api/                    HTTP/JSON dla Reacta
│   │   │   ├── ScanRestController.java
│   │   │   └── dto/                dane żądania i odpowiedzi HTTP
│   │   ├── folder/                 sprawdzenie wybranego folderu
│   │   │   ├── ScanFolderValidator.java
│   │   │   ├── AllowedRoot.java    zweryfikowana ścieżka folderu
│   │   │   └── InvalidScanFolderException.java
│   │   ├── image/                  wykonanie skanowania obrazów
│   │   │   └── ImageScanner.java
│   │   ├── result/                 dane wyniku bieżącego skanu
│   │   │   ├── ScanResult.java     folder + accepted + rejected + summary
│   │   │   ├── ScanSummary.java    nowe / ponowne / bez zmian / odrzucone
│   │   │   ├── AcceptedImage.java
│   │   │   ├── RejectedFile.java
│   │   │   └── RejectionReason.java
│   │   ├── knownfiles/             dane poprzednio poznanych plików
│   │   │   └── KnownFile.java      ścieżka względna, rozmiar, data, SHA-256
│   │   ├── hashing/
│   │   │   └── FileHasher.java     obliczanie SHA-256
│   │   ├── workers/
│   │   │   ├── ScanWorkersConfiguration.java  pula wątków skanera
│   │   │   └── ScanJobRunnerConfiguration.java  osobny wątek całego zadania
│   │   └── prototype/              starszy ekran Thymeleaf, nie React
│   └── catalog/                    funkcja: zapisz i odczytaj katalog obrazów
│       ├── reading/                odczyt danych z katalogu
│       │   ├── CatalogReader.java  zapisane obrazy i strony dla UI
│       │   └── KnownFilesReader.java  lekka mapa metadanych dla skanera
│       ├── writing/                zapis i aktualizacja danych katalogu
│       │   └── CatalogUpdater.java zapis wyników po skanowaniu
│       ├── api/                    HTTP/JSON dla Reacta
│       ├── dto/                    CatalogFileDto i CatalogPageDto
│       ├── preview/                odczyt źródła, bezpieczne PNG i ograniczony cache
│       └── database/               encje i repozytoria JPA/PostgreSQL
├── src/main/resources/
│   ├── db/migration/               Flyway: struktura tabel SQL
│   ├── templates/scanning/         widok starszego prototypu Thymeleaf
│   └── application*.properties     konfiguracja Springa
├── src/test/java/pl/pum/screensort/ testy w pakietach odpowiadających kodowi
├── compose.yaml                    lokalny PostgreSQL w Dockerze
└── demo-images/                    przykładowe pliki do ręcznego skanu
```

`pl.pum.screensort` to nazwa pakietu Javy. Katalogi pod `src/main/java`
odzwierciedlają kolejne części tej nazwy. Testy mają analogiczne pakiety pod
`src/test/java`.

Pakiet Python ma własną przestrzeń nazw `screensort_analysis`, nie kopię
pakietów Javy. `__init__.py` oznacza pakiet/podpakiet. Obecny serwis nie jest
wywoływany przez Springa ani Reacta; health jawnie raportuje brak analizy.
Instrukcja, role plików i następne kroki:
[`analysis-service/README.md`](../analysis-service/README.md).
Granice i uzasadnienie: [ADR-004](adr/ADR-004-local-python-analysis-service.md).

## Co oznaczają te nazwy

| Nazwa | Rola | Najbliższa analogia frontendowa |
|---|---|---|
| `api` | Przyjmuje żądania HTTP i oddaje JSON. | Handler żądania do backendu. |
| `dto` | Kształt danych przekazywanych przez API; nie jest tabelą bazy. | Typ obiektu przesyłanego przez `fetch`. |
| `workflow` | `ScanWorkflow` łączy walidację, odczyt znanych plików, skanowanie i zapis; sam nie czyta każdego pliku. | Funkcja koordynująca kilka kroków. |
| `jobs` | Opis konkretnego uruchomienia, start w tle, bieżący status i ograniczona historia. | Stan operacji, który UI może odczytać po ID. |
| `folder` | Sprawdza wybraną ścieżkę i zwraca `AllowedRoot`. Zawiera także błąd nieprawidłowego folderu. | Walidacja danych wejściowych, ale po stronie backendu. |
| `image` | `ImageScanner` czyta pliki, odrzuca nieprawidłowe i tworzy `ScanResult`. | Logika przetwarzania danych. |
| `result` | Opisy zaakceptowanych i odrzuconych plików, powody odrzucenia oraz zbiorczy wynik. | Dane zwrócone przez funkcję przetwarzającą. |
| `knownfiles` | `KnownFile` opisuje poprzednio zapisane metadane jednego pliku. | Dane do porównania z aktualnym stanem, nie zawartość pliku. |
| `hashing` | Liczy skrót zawartości pliku. | Małe narzędzie używane przez skaner. |
| `workers` | Konfiguracja puli wątków używanej przez skaner. | Ograniczona liczba równolegle wykonujących pracę zadań. |
| `database` | Mapowanie tabel na encje JPA i gotowe operacje repozytoriów. | Warstwa dostępu do trwałych danych. |
| `reading` | Odczyt danych katalogu dla konkretnego odbiorcy: UI lub skanera. | Funkcja pobierająca i przygotowująca dane do użycia. |
| `writing` | Zapis nowych rekordów i aktualizacja istniejących po skanie. | Funkcja synchronizująca wynik z trwałym magazynem. |
| `prototype` | Zachowany ekran Thymeleaf pod `/`; oddzielony od API Reacta. | Stary widok, nadal działający. |
| `preview` | Odczyt obrazu po ID, walidacja źródła, miniatura i większy podgląd. | Osobna funkcja przygotowująca obraz dla widoku, nie skanująca całego folderu. |

`CatalogFileDto` jest w `catalog/dto`, ponieważ używa go zarówno
`CatalogReader`, jak i `CatalogRestController` (oraz obecny podgląd w konsoli).
Nie należy do samej bazy danych. DTO skanowania są w `scanning/api/dto`, bo
opisują wyłącznie wejście i wyjście HTTP; `ScanWorkflow` korzysta z `ScanResult`,
a nie z tych DTO.

`KnownFile` w `scanning/knownfiles` opisuje poprzednio zapisany stan jednego pliku:
ścieżkę względną, rozmiar, datę modyfikacji i SHA-256. Nie jest encją JPA ani
zawartością zdjęcia. `KnownFilesReader` w `catalog/reading` przyjmuje
zweryfikowany `AllowedRoot`, odczytuje z bazy cztery pola dla tego folderu
i buduje mapę `relativePath -> KnownFile`. Zwraca niemodyfikowalną migawkę
przez `Map.copyOf`; nie otwiera zdjęć ani nie porównuje aktualnych metadanych.
Odczyt sprawdza `KnownFilesReaderJpaTest` na tymczasowym PostgreSQL-u.
Reader jest podłączony do `ScanWorkflow`, który przekazuje mapę i `AllowedRoot`
do `ImageScanner.scan`, a skaner przekazuje je do `processFile`. Worker szuka
pliku po ścieżce względnej. Po tanich kontrolach porównuje rozmiar i datę
modyfikacji z poprzednim opisem. Przy zgodności używa zapisanego SHA-256,
pomijając dekodowanie obrazu i ponowne hashowanie. Nowy plik lub niezgodność
metadanych oznacza pełny odczyt i hash. Czasy porównujemy po obcięciu do
mikrosekund, tak samo jak przy zapisie do PostgreSQL.
Zgodność metadanych nie dowodzi identyczności zawartości. Numerowane kroki
w readerze pozostają jako schemat już uzupełnionej implementacji.

`KnownFilesReader` pozostaje w `catalog/reading`, ponieważ korzysta z bazy
katalogu. Jego odbiorcą jest skaner, ale nie oznacza to, że reader ma otwierać
obrazy z dysku. `CatalogReader` przygotowuje dane dla UI, a `KnownFilesReader`
przygotowuje lżejsze dane dla skanowania. To dwa różne cele odczytu tej samej bazy.

Prywatne `ImageScanType`, `FileOutcome`, `ProcessedFile` i `ScanTiming` pozostają wewnątrz
`ImageScanner`: są szczegółami jego pracy, nie wspólnym wynikiem dla innych klas.

`ImageScanType` opisuje sposób przetworzenia zaakceptowanego obrazu:
`NEW`, `RESCANNED` lub `UNCHANGED`. Worker zwraca tę etykietę razem z obrazem
w `FileOutcome.Accepted`. Główny wątek skanu rozdziela wyniki na dwie istniejące
listy i zwiększa liczniki. Workery nie modyfikują wspólnego podsumowania.
`ScanSummary` zawiera cztery liczby dotyczące jednego skanu. `ScanResult`
pilnuje, żeby suma trzech liczników obrazów odpowiadała liście `accepted`,
a `rejectedFiles` — liście `rejected`.

`ScanResultDto` nadal obsługuje stare API. React otrzymuje `summary` w `ScanJob`,
a komponent `scanning/ScanSummary.tsx` tylko je wyświetla.
Nie ma czterech dodatkowych stanów Reacta ani nowych kolumn w bazie.

`CatalogPageDto` zawiera opisy plików jednej strony oraz jej numer, rozmiar,
liczbę wszystkich zapisanych plików folderu i `hasNext`. `CatalogReader.readPage`
przekazuje `PageRequest` do repozytorium: PostgreSQL zwraca tylko potrzebną
porcję, posortowaną po ścieżce względnej. React przechowuje tę odpowiedź jako
`catalogPage`; `CatalogGallery` dostaje jej listę, nie tworzymy drugiej kopii w stanie.
Kliknięcie przycisku zmienia `catalogPageNumber` i powoduje nowe żądanie.
Nie oznacza to skanowania dysku, dekodowania zdjęć ani obliczania SHA-256.
`KnownFilesReader` nadal odczytuje wszystkie znane opisy dla skanowanego folderu,
ponieważ porównanie plików nie może być ograniczone stroną oglądaną w UI.

## Jak płyną dane

```text
Stare synchroniczne API (React już go nie używa):
  → POST /api/scan
  → scanning/api/ScanRestController
  → scanning/jobs/ScanJobManager.scanAndSave (ta sama blokada jednego skanu)
  → scanning/workflow/ScanWorkflow
       → scanning/folder/ScanFolderValidator → AllowedRoot
       → catalog/reading/KnownFilesReader → repozytoria → PostgreSQL
         → Map<String, KnownFile> (poprzednie opisy plików)
  → scanning/image/ImageScanner (AllowedRoot + mapa znanych plików)
       → scanning/workers (pula wątków do przetwarzania plików)
       → tanie kontrole + porównanie metadanych z KnownFile
          → zgodne: poprzedni SHA-256, etykieta UNCHANGED
          → nowe/niezgodne: ImageIO + FileHasher, etykieta NEW/RESCANNED
          → błąd kontroli: RejectedFile
       → scanning/result/ScanResult (accepted + rejected + ScanSummary)
  → catalog/writing/CatalogUpdater
  → catalog/database/*Repository → PostgreSQL
  → scanning/api/dto/ScanResultDto → JSON → dawny klient

Aktualny start w React:
  → POST /api/scan/jobs → ScanJobManager.start → HTTP 202 + ID
  → osobny scanJobRunner wykonuje ten sam ScanWorkflow w tle
  → ScanProgressListener aktualizuje ScanJob: PREPARING / SCANNING / SAVING
  → po udanym zapisie COMPLETED; przy błędzie FAILED
  → GET /api/scan/jobs/{id} odczytuje niemodyfikowalną migawkę, nie bazę ani dysk
  → React powtarza ten GET około raz na sekundę i pokazuje etap + liczniki
  → COMPLETED: GET katalogu i GET /api/scan/jobs/{id}/rejections (do 100 przykładów)
  → FAILED: błąd; nie udajemy udanego zapisu ani nie uruchamiamy kolejnego skanu

Wejście/odświeżenie katalogu lub kliknięcie „Następna” w React
  → GET /api/catalog/page?folderPath=...&pageNumber=N&pageSize=50
  → catalog/api/CatalogRestController
       → kontrola numeru i rozmiaru strony (błąd: HTTP 400)
  → catalog/reading/CatalogReader.readPage
       → scanning/folder/ScanFolderValidator
       → PageRequest → catalog/database/*Repository → PostgreSQL
         → Page<FileRecordEntity> (porcja rekordów + liczba wszystkich)
  → catalog/dto/CatalogPageDto (files: CatalogFileDto[] + metadane strony)
  → JSON → React: CatalogGallery i przyciski stron

Widoczny kafelek / wybór większego podglądu
  → CatalogImage → GET /api/catalog/files/{id}/thumbnail lub /preview
  → catalog/api/CatalogImageController
  → catalog/preview/CatalogImagePreview
       → CatalogImageSourceReader → repozytorium → PostgreSQL (transakcja read-only)
       → ScanFolderValidator + kontrola ścieżki i zwykłego pliku
       → ograniczony cache lub dekoder ImageReader (max 2 naraz)
       → zmniejszenie i zakodowanie PNG w pamięci
  → PNG → okrągła miniatura / ImageDetailsTree

Wybór zdjęcia w CatalogGallery (bez nowego skanu)
  → selectedId + bieżąca strona DTO
  → CatalogDetailsPanel → prawdziwe metadane obok galerii
  → AnimatedBranch → zmiana stanu i animacja CSS, nie zapytanie do bazy
  → Powiększ → ImageDetailsDialog → natywny dialog.showModal() → podgląd i tło
  → jedno ImageDetailsTree wewnątrz okna; galeria zostaje na miejscu
  → useTreeConnections mierzy podgląd i gałęzie
  → treeConnections wylicza dekoracyjne linie SVG
  → rozwinięcie gałęzi / zmiana szerokości: dopasowanie geometrii, nie zapytanie do bazy
  → X / Escape / kliknięcie tła: zamknięcie i powrót fokusu do przycisku Powiększ

Analiza · DEMO (nie dotyczy wybranego zdjęcia)
  → AnalysisDemo → examples.ts → fikcyjny opis, tekst, kategoria i tagi
  → ReviewActions → zmiana lokalnego stanu Reacta
  → brak analizy obrazu, żądania do API, zapisu bazy i zmiany plików
```

Ważne: `ScanResult` to **wynik bieżącego odczytu dysku**. `FileRecordEntity` to
**wiersz zapisany w PostgreSQL**. `CatalogFileDto` to **opis wysyłany do UI**.
Mogą zawierać podobne pola, ale mają różne zadania. `ScreenSortApplication`
tylko uruchamia Springa; nie jest miejscem na cały kod programu.

Galeria nie zapisuje nowego wyniku skanu. Jej szczegółowy opis, mapa nowych
plików oraz ograniczenia bezpieczeństwa i pamięci są w
[`galeria-i-podglady.md`](galeria-i-podglady.md). Miniatury nie trafiają do folderu
źródłowego ani do tabeli bazy. Metadane są z ostatniego skanu, podgląd z dysku.

Nowe komponenty mają style `.module.css` obok plików TSX. Globalne `index.css`
i `App.css` przechowują font, kolory, wspólne przyciski i ramę aplikacji.
Obraz, modal i większe drzewo mają osobne istniejące klasy w odpowiednich plikach CSS;
nie ma już zbiorczego `CatalogGallery.css`. Pełna chronologia zmian i wyjaśnienie:
[`od-zadan-skanowania-do-ui.md`](od-zadan-skanowania-do-ui.md).

## Gdzie dodać następną rzecz

- Nowy warunek dopuszczenia folderu: `scanning/folder`.
- Nowa reguła rozpoznawania pliku: `scanning/image`.
- Dane wyniku skanu: `scanning/result`; dane poprzednio znanego pliku: `scanning/knownfiles`.
- Połączenie etapów skanowania: `scanning/workflow`.
- Obsługa uruchomienia i stanu zadania: `scanning/jobs`; konfiguracja wykonawcy: `scanning/workers`.
- Nowe pole odpowiedzi skanowania dla Reacta: `scanning/api/dto` i typ w React.
- Nowe pole trwale zapisane w katalogu: migracja w `db/migration`, encja w
  `catalog/database`, zapis w `catalog/writing`, odczyt w `catalog/reading`
  i — jeśli UI ma je widzieć — DTO.
- Nowa operacja HTTP dotycząca katalogu: `catalog/api`.

Nie twórz pustego pakietu „na zapas”. Rozbudowuj istniejącą funkcję dopiero,
gdy pojawi się konkretna potrzeba. Źródłowe obrazy pozostają tylko do odczytu;
przyszłe porządkowanie ma tworzyć zatwierdzone kopie, nigdy zmieniać oryginałów.

Dokładne wyjaśnienie nowych plików i zmian istniejących metod:
[`scanjob-krok-po-kroku.md`](scanjob-krok-po-kroku.md). Jedno aktywne zadanie,
20 opisów i do 100 przykładów odrzuceń na zadanie w pamięci, osobny wykonawca
workflow; React korzysta z nowego API zadań.
