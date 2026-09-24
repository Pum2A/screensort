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
│   │   ├── ScanWorkflow.java       kolejność: skanowanie → zapis wyniku
│   │   ├── api/                    HTTP/JSON dla Reacta
│   │   │   ├── ScanRestController.java
│   │   │   └── dto/                dane żądania i odpowiedzi HTTP
│   │   ├── safety/                 czy folder wolno skanować
│   │   ├── image/                  klasyfikacja plików, metadane, wynik skanu
│   │   ├── hashing/                obliczanie SHA-256
│   │   └── prototype/              starszy ekran Thymeleaf, nie React
│   └── catalog/                    funkcja: zapisz i odczytaj katalog obrazów
│       ├── CatalogUpdater.java     zapis/aktualizacja po skanowaniu
│       ├── CatalogReader.java      odczyt zapisanych obrazów
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
| `ScanWorkflow` | Łączy istniejący skaner z zapisem katalogu; sam nie czyta każdego pliku. | Funkcja koordynująca kilka kroków. |
| `safety` | Sprawdza wybraną ścieżkę i zwraca bezpieczny `AllowedRoot`. | Walidacja danych wejściowych, ale po stronie backendu. |
| `image` | Czyta pliki, odrzuca nieprawidłowe i tworzy `ScanResult`. | Logika przetwarzania danych. |
| `hashing` | Liczy skrót zawartości pliku. | Małe narzędzie używane przez skaner. |
| `database` | Mapowanie tabel na encje JPA i gotowe operacje repozytoriów. | Warstwa dostępu do trwałych danych. |
| `prototype` | Zachowany ekran Thymeleaf pod `/`; oddzielony od API Reacta. | Stary widok, nadal działający. |

`CatalogFileDto` jest w `catalog/dto`, ponieważ używa go zarówno
`CatalogReader`, jak i `CatalogRestController` (oraz obecny podgląd w konsoli).
Nie należy do samej bazy danych. DTO skanowania są w `scanning/api/dto`, bo
opisują wyłącznie wejście i wyjście HTTP; `ScanWorkflow` korzysta z `ScanResult`,
a nie z tych DTO.

## Jak płyną dane

```text
Kliknięcie „Scan folder” w React
  → POST /api/scan
  → scanning/api/ScanRestController
  → scanning/ScanWorkflow
  → scanning/image/ImageScanner
       → scanning/safety/ScanFolderValidator
       → scanning/hashing/FileHasher
       → ScanResult (accepted + rejected)
  → catalog/CatalogUpdater
  → catalog/database/*Repository → PostgreSQL
  → scanning/api/dto/ScanResultDto → JSON → React

Wejście/odświeżenie widoku katalogu w React
  → GET /api/catalog/files?folderPath=...
  → catalog/api/CatalogRestController
  → catalog/CatalogReader
       → scanning/safety/ScanFolderValidator
       → catalog/database/*Repository → PostgreSQL
  → catalog/dto/CatalogFileDto → JSON → React
```

Ważne: `ScanResult` to **wynik bieżącego odczytu dysku**. `FileRecordEntity` to
**wiersz zapisany w PostgreSQL**. `CatalogFileDto` to **opis wysyłany do UI**.
Mogą zawierać podobne pola, ale mają różne zadania. `ScreenSortApplication`
tylko uruchamia Springa; nie jest miejscem na cały kod programu.

## Gdzie dodać następną rzecz

- Nowy warunek dopuszczenia folderu: `scanning/safety`.
- Nowa reguła rozpoznawania pliku: `scanning/image`.
- Nowe pole odpowiedzi skanowania dla Reacta: `scanning/api/dto` i typ w React.
- Nowe pole trwale zapisane w katalogu: migracja w `db/migration`, encja w
  `catalog/database`, zapis/odczyt w `catalog` i — jeśli UI ma je widzieć — DTO.
- Nowa operacja HTTP dotycząca katalogu: `catalog/api`.

Nie twórz pustego pakietu „na zapas”. Rozbudowuj istniejącą funkcję dopiero,
gdy pojawi się konkretna potrzeba. Źródłowe obrazy pozostają tylko do odczytu;
przyszłe porządkowanie ma tworzyć zatwierdzone kopie, nigdy zmieniać oryginałów.
