# ScreenSort — mapa obecnego projektu

Ten dokument opisuje kod, który **już istnieje**. To nie jest lista pakietów do
utworzenia na przyszłość. Projekt jest jedną aplikacją Spring Boot z frontendem
React, a nie zbiorem mikroserwisów.

## Gdzie czego szukać

```text
screenSorter/
├── frontend/src/                 React: ekran, stan i wywołania /api
├── src/main/java/pl/pum/screensort/
│   ├── ScreenSortApplication.java  uruchamia Spring Boot
│   ├── scanning/                   funkcja: sprawdź folder i przeskanuj obrazy
│   │   ├── workflow/               kolejność kroków całego skanowania
│   │   │   └── ScanWorkflow.java   walidacja → znane pliki → skan → zapis
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
│   │   │   ├── ScanResult.java     folder + accepted + rejected
│   │   │   ├── AcceptedImage.java
│   │   │   ├── RejectedFile.java
│   │   │   └── RejectionReason.java
│   │   ├── knownfiles/             dane poprzednio poznanych plików
│   │   │   └── KnownFile.java      ścieżka względna, rozmiar, data, SHA-256
│   │   ├── hashing/
│   │   │   └── FileHasher.java     obliczanie SHA-256
│   │   ├── workers/
│   │   │   └── ScanWorkersConfiguration.java  pula wątków skanera
│   │   └── prototype/              starszy ekran Thymeleaf, nie React
│   └── catalog/                    funkcja: zapisz i odczytaj katalog obrazów
│       ├── reading/                odczyt danych z katalogu
│       │   ├── CatalogReader.java  zapisane obrazy dla UI
│       │   └── KnownFilesReader.java  lekka mapa metadanych dla skanera
│       ├── writing/                zapis i aktualizacja danych katalogu
│       │   └── CatalogUpdater.java zapis wyników po skanowaniu
│       ├── api/                    HTTP/JSON dla Reacta
│       ├── dto/                    opis pliku zwracany z katalogu
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

## Co oznaczają te nazwy

| Nazwa | Rola | Najbliższa analogia frontendowa |
|---|---|---|
| `api` | Przyjmuje żądania HTTP i oddaje JSON. | Handler żądania do backendu. |
| `dto` | Kształt danych przekazywanych przez API; nie jest tabelą bazy. | Typ obiektu przesyłanego przez `fetch`. |
| `workflow` | `ScanWorkflow` łączy walidację, odczyt znanych plików, skanowanie i zapis; sam nie czyta każdego pliku. | Funkcja koordynująca kilka kroków. |
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
do `ImageScanner.scan`, a skaner przekazuje je do `processFile`. Worker nie
wyszukuje jeszcze pliku w mapie ani nie porównuje metadanych, więc nadal
przetwarza ponownie znane pliki. Wyszukiwanie będzie następnym wspólnym krokiem.
Numerowane TODO w klasie pozostają jako schemat już uzupełnionej implementacji.

`KnownFilesReader` pozostaje w `catalog/reading`, ponieważ korzysta z bazy
katalogu. Jego odbiorcą będzie skaner, ale nie oznacza to, że reader ma otwierać
obrazy z dysku. `CatalogReader` przygotowuje dane dla UI, a `KnownFilesReader`
przygotowuje lżejsze dane dla skanowania. To dwa różne cele odczytu tej samej bazy.

Prywatne `FileOutcome`, `ProcessedFile` i `ScanTiming` pozostają wewnątrz
`ImageScanner`: są szczegółami jego pracy, nie wspólnym wynikiem dla innych klas.

## Jak płyną dane

```text
Kliknięcie „Scan folder” w React
  → POST /api/scan
  → scanning/api/ScanRestController
  → scanning/workflow/ScanWorkflow
       → scanning/folder/ScanFolderValidator → AllowedRoot
       → catalog/reading/KnownFilesReader → repozytoria → PostgreSQL
         → Map<String, KnownFile> (poprzednie opisy plików)
  → scanning/image/ImageScanner (AllowedRoot + mapa; mapa jeszcze nieużywana)
       → scanning/workers (pula wątków do przetwarzania plików)
       → scanning/hashing/FileHasher
       → scanning/result/ScanResult (accepted + rejected)
  → catalog/writing/CatalogUpdater
  → catalog/database/*Repository → PostgreSQL
  → scanning/api/dto/ScanResultDto → JSON → React

Wejście/odświeżenie widoku katalogu w React
  → GET /api/catalog/files?folderPath=...
  → catalog/api/CatalogRestController
  → catalog/reading/CatalogReader
       → scanning/folder/ScanFolderValidator
       → catalog/database/*Repository → PostgreSQL
  → catalog/dto/CatalogFileDto → JSON → React
```

Ważne: `ScanResult` to **wynik bieżącego odczytu dysku**. `FileRecordEntity` to
**wiersz zapisany w PostgreSQL**. `CatalogFileDto` to **opis wysyłany do UI**.
Mogą zawierać podobne pola, ale mają różne zadania. `ScreenSortApplication`
tylko uruchamia Springa; nie jest miejscem na cały kod programu.

## Gdzie dodać następną rzecz

- Nowy warunek dopuszczenia folderu: `scanning/folder`.
- Nowa reguła rozpoznawania pliku: `scanning/image`.
- Dane wyniku skanu: `scanning/result`; dane poprzednio znanego pliku: `scanning/knownfiles`.
- Połączenie etapów skanowania: `scanning/workflow`.
- Nowe pole odpowiedzi skanowania dla Reacta: `scanning/api/dto` i typ w React.
- Nowe pole trwale zapisane w katalogu: migracja w `db/migration`, encja w
  `catalog/database`, zapis w `catalog/writing`, odczyt w `catalog/reading`
  i — jeśli UI ma je widzieć — DTO.
- Nowa operacja HTTP dotycząca katalogu: `catalog/api`.

Nie twórz pustego pakietu „na zapas”. Rozbudowuj istniejącą funkcję dopiero,
gdy pojawi się konkretna potrzeba. Źródłowe obrazy pozostają tylko do odczytu;
przyszłe porządkowanie ma tworzyć zatwierdzone kopie, nigdy zmieniać oryginałów.
