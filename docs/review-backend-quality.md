# Przegląd niezawodności backendu — co naprawiono i co zostało

Sprawdzone 2026-10-21. Zakres: backend Java (`src/main/java`) oraz jego testy.
To przegląd kodu, nie deklaracja gotowości produkcyjnej i nie audyt
bezpieczeństwa. Frontend, serwis Python, CI/CD i dokumentacja mają osobne
luki, opisane na końcu.

Przegląd szukał czterech rzeczy: wycieku danych prywatnych, błędów, które
wyglądają jak awaria serwera, brakujących zabezpieczeń współbieżnych i miejsc,
gdzie koszt rośnie z liczbą plików.

## Co naprawiono

| Obszar | Problem | Gdzie | Co zmieniono |
| --- | --- | --- | --- |
| Prywatność | Ścieżki bezwzględne i SHA-256 wypisywane na konsolę | `ImageScanner`, `CatalogUpdater` | Zamiast `System.out` jest logger, a do logu nie trafiają ścieżki, nazwy ani hashe. Liczniki zostają w `ScanSummary`. |
| Wznowienie analizy | `POST .../control` z akcją `RESUME` zwracał HTTP 500, gdy baza odrzucała drugi aktywny batch | `BatchManager.resume`, `BatchStore.resume` | `DataIntegrityViolationException` jest łapane i zamieniane na 409 „Inna analiza folderu jest w toku”, tak jak w `start()`. |
| Klient analizy | Każde `connect()` tworzyło nowy `HttpClient` i nigdy go nie zamykało — zostawał wątek selektora i pula wątków | `PythonAnalysisClient` | Klient jest polem instancji. Poprzedni jest zamykany przy zmianie adresu, bieżący — przy zamykaniu aplikacji (`@PreDestroy`). |
| Diagnostyka | `catch (RuntimeException)` gubił przyczynę awarii | `BatchManager`, `ExportManager`, `AnalysisJobManager` | Przyczyna trafia do logu. Oczekiwane błędy analizy są logowane jako ostrzeżenie bez stosu, niespodziewane jako błąd ze stosem. |
| Błędna ścieżka | `Path.of()` na niedozwolonym znaku dawał HTTP 500 | `CatalogRestController` | `InvalidPathException` ma własny handler i zwraca 400. |
| Eksport | `ExportStore.next()` nie blokował wiersza — dwie instancje aplikacji skopiowałaby ten sam plik | `ExportStore`, migracja `V9` | Plik jest zajmowany w tej samej transakcji, która go wybiera (`for update` + stan `RUNNING`), tak jak w `BatchStore`. Po restarcie stan wraca do `PENDING`. |
| Odczyt katalogu | `GET /api/catalog/files` ładował cały katalog do pamięci | `CatalogReader.read` | Odczyt ma limit 1000 rekordów. Powyżej tego endpoint zwraca 400 z wskazaniem `/api/catalog/page`. |
| Rozpoznawanie obrazu | `ImageIO.read()` dekodował każdy plik w całości — duży PNG zjadał setki MB RAM na wątek | `ImageScanner.isReadableImage` | Format i wymiary są czytane z nagłówka, bez dekodowania. Limity są te same co w podglądzie: 40 mln pikseli i 20 000 px na bok. |

## Zmienione zachowanie widoczne dla użytkownika

Dwie zmiany są odczuwalne i nie są wyłącznie wewnętrzne:

- **Plik o nazwie `.png`, który w środku jest GIF-iem, nie wchodzi już do
  katalogu.** Podgląd obsługuje PNG i JPEG, więc wcześniej taki plik trafiał do
  katalogu, a potem nie dawał się wyświetlić. Teraz skan odrzuca go od razu,
  jako `UNREADABLE_IMAGE`.
- **`GET /api/catalog/files` odmawia przy katalogu większym niż 1000 obrazów.**
  Interfejs używa `/api/catalog/page` i nie zauważy tej zmiany. Zmiana
  chroni proces przed jednym żądaniem, które mogło wczytać cały katalog.

## Co zostało w backlogu

Poniższe problemy są potwierdzone w kodzie, ale ich naprawa wymaga osobnej
decyzji albo zmiany kontraktu. Nie są objęte tym przeglądem.

| Problem | Gdzie | Dlaczego nie teraz |
| --- | --- | --- |
| Dwa zapytania do bazy na każdy plik przy zapisie katalogu | `CatalogUpdater.update` | Przy 10 000 plików to 20 000 round-tripów w jednej transakcji. Naprawa wymaga wczytania rekordów folderu jednym zapytaniem i `saveAll` z batchowaniem, czyli przepisania zapisu katalogu. |
| Trzy transakcje na plik w pętli eksportu | `ExportManager.run` | Kopiowanie celowo odbywa się poza transakcją, więc scalenie transakcji wymaga przemyślenia, co zrobić przy przerwaniu w połowie pliku. |
| `OFFSET` przy głębokiej paginacji | `ReviewStore`, `ExportStore` | Strona jest walidowana do 1 000 000, czyli `OFFSET` do 50 mln wierszy. Właściwa naprawa to paginacja klawuczowa, a nie podbicie limitu. |
| Brak ponawiania po 429/503 między Javą a Pythonem | `PythonAnalysisClient`, `analysis-service` | Klient robi jedną próbę, serwis od razu odrzuca kolejkę. Wymaga ustalenia, ile ponowień i z jakim odstępem. |
| Wyścig między sprawdzeniem katalogu a utworzeniem pliku tymczasowego | `OrganizedFileCopier` | Podmiana katalogu docelowego na symlink w oknie między `requireDirectory` a `createTempFile`. Wymaga pracy na kanałach plików z `NOFOLLOW_LINKS`. |
| Eksport do folderu wewnątrz folderu źródłowego | `ExportManager.start` | Następny skan zobaczy własne kopie. Wymaga sprawdzenia ścieżki docelowej przed startem. |
| Brak sprawdzenia wolnego miejsca na dysku | `OrganizedFileCopier` | Eksport może zakończyć się w połowie z powodu braku miejsca. |

## Luki poza backendem

Te ustalenia dotyczą innych części repo i nie zostały w tym przeglądzie
zmienione.

- **Bramkowanie w CI.** Brakuje typechecku Pythona, `ruff format --check`,
  `cargo clippy`, skanowania zależności (dependabot, CodeQL), pomiaru pokrycia
  kodu i progu dla niego. Kod jest napisany bezpiecznie, ale bramki nie
  pilnują regresji.
- **Rozjazd wersji w pięciu miejscach:** `tauri.conf.json` 0.2.4, README 0.2.3,
  `pom.xml` 0.0.1-SNAPSHOT, `package.json` 0.0.0, `pyproject.toml` 0.1.0.
- **Brak `LICENSE`, `CONTRIBUTING.md` i `SECURITY.md`.** Brak licencji oznacza
  domyślnie brak praw do użycia kodu.
- **Sprawl dokumentacji.** 37 plików w `docs/`, trzy opisują ten sam skan, a
  `docs/device-handoff.md` to 880 linii logu. Brakuje indeksu, który mówi,
  który dokument jest aktualny.
- **Dokumenty mylące wobec kodu:** `docs/struktura-projektu.md` i sekcja
  „Stan wdrożenia" w `ADR-004` opisują etap sprzed implementacji klienta
  Javy i endpointu analizy.
- **Brak metryk i access logu w serwisie Python.** Serwis, który wykonuje
  sekundy pracy CPU na obraz, nie eksponuje czasu analizy ani liczby 429/504.
- **Brak podpisu Authenticode** instalatora Windows.

## Weryfikacja

Nowe testy:

| Test | Co sprawdza |
| --- | --- |
| `PythonAnalysisClientConnectionTest` | Ponowne połączenie wymienia klienta i zamyka poprzedni. |
| `BatchManagerTest.resumeReportsConflictWhenTheDatabaseAlreadyRunsAnotherBatch` | Wznowienie przy zajętej bazie zwraca 409, nie 500. |
| `CatalogRestControllerTest` | Błędna ścieżka i zbyt duży katalog zwracają 400, nie 500. |
| `ImageScannerTest.rejectsGifContentBehindPngName` | GIF pod nazwą `.png` nie wchodzi do katalogu. |
| `ImageScannerTest.rejectsImageBeyondPreviewLimitsWithoutDecodingIt` | Plik deklarujący 10 mln pikseli jest odrzucany z nagłówka. |

Uruchomienie:

```powershell
.\mvnw.cmd -B test "-Dtest=ImageScannerTest,BatchManagerTest,PythonAnalysisClientConnectionTest,CatalogRestControllerTest"
```

Testy bazodanowe (eksport, wznowienie, migawka decyzji) wymagają bazy.
Bez Dockera uruchamia się je na dołączonym, odizolowanym PostgreSQL:

```powershell
./scripts/verify-native-tests.ps1 -Tests 'ReviewJpaTest,BatchStoreJpaTest,NamingJpaTest'
```

Migracja `V9` rozszerza status pozycji eksportu o `RUNNING`. Katalog bez
tej migracji nie może obsłużyć zajmowania pliku, więc przed wdrożeniem
migracja musi dojechać do bazy — Flyway wykonuje to przy starcie aplikacji.
