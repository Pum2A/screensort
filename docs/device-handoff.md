# ScreenSort — przenoszenie pracy między urządzeniami i rozmowami AI

Ostatnia aktualizacja struktury i bieżącego etapu: 2026-10-07

Ten dokument jest bieżącym skrótem projektu. Użyj go po zmianie komputera albo
przy rozpoczęciu nowej rozmowy z agentem AI.

## Kanoniczne repozytorium

Jedynym aktywnym repozytorium jest prywatne repozytorium:

```text
https://github.com/Pum2A/screensort
```

Domyślna gałąź to `main`. Stare repozytorium
`<lokalny-katalog-projektu> jest wyłącznie kopią zapasową. Nie wolno
go jeszcze usuwać ani modyfikować i nie wolno kopiować z niego katalogu `.git`.

## Sposób współpracy

- Rozmawiaj z właścicielem po polsku.
- Właściciel zna podstawy programowania i frontend, ale uczy się Javy, Springa
  oraz backendu.
- Nowe pojęcia wyjaśniaj krótko i konkretnie; stosuj analogie frontendowe.
- Dziel pracę na małe, możliwe do zrozumienia fragmenty.
- Właściciel chce sam pisać i rozumieć istotny kod. Najpierw podawaj tok
  myślenia lub pseudokod, chyba że wyraźnie poprosi o implementację.
- Każdą spójną funkcję lub poprawkę należy sprawdzić, zacommitować i wypchnąć.
- Nie włączaj do commita cudzych lokalnych zmian bez ich sprawdzenia.

## Aktualny stos technologiczny

- Java 25 (Eclipse Temurin), Spring Boot 4.1.1 i Maven Wrapper 3.9.16.
- Spring MVC oraz lokalne REST API.
- PostgreSQL uruchamiany przez Docker Compose i migracje Flyway.
- React 19, TypeScript 6 i Vite 8.
- Tauri 2 + Rust: działa okno developerskie i natywny picker folderu.
- JUnit Jupiter.

```text
groupId:      pl.pum
artifactId:   screensort
application:  ScreenSort
base package: pl.pum.screensort
main class:   ScreenSortApplication
```

## Co już działa

1. Spring Boot uruchamia aplikację, a tymczasowy ekran Thymeleaf działa pod `/`.
2. `ScanFolderValidator` sprawdza wybrany katalog i tworzy `AllowedRoot`.
3. Skanowane są bezpośrednie elementy katalogu — bez skanowania rekurencyjnego.
4. Linki symboliczne, katalogi, nieobsługiwane rozszerzenia, pliki powyżej
   50 MiB i obrazy niemożliwe do odczytania trafiają do listy odrzuconych wraz
   z powodem.
5. Poprawne pliki PNG/JPG/JPEG stają się rekordami `AcceptedImage` z metadanymi
   i hashem SHA-256.
6. `ScanResult` zawiera oddzielne listy `accepted` i `rejected` oraz `ScanSummary`.
7. `POST /api/scan` przyjmuje JSON z `folderPath`; błędne lub puste żądanie
   zwraca HTTP 400.
8. React wywołuje lokalne API, pokazuje cztery kompaktowe liczniki ostatniego
   skanu oraz rozwijane powody odrzucenia. Zapisane obrazy są w galerii,
   bez powielania listy zaakceptowanych nad katalogiem.
9. `ScanWorkflow` przekazuje wynik do `CatalogUpdater`, który zapisuje nowe
   pliki w PostgreSQL albo aktualizuje istniejące rekordy przy kolejnym skanie.
10. `GET /api/catalog/page` przez `CatalogReader.readPage` odczytuje stronę
    zapisanych obrazów. React pokazuje maksymalnie 50 kafelków, liczbę wszystkich
    zapisanych plików oraz przyciski stron. Katalog odczytywany jest po uruchomieniu,
    wyborze folderu i po udanym skanowaniu. Odczyt galerii nie uruchamia skanu.
11. `FileHasher` jest komponentem Springa i może być wstrzyknięty do
    `ImageScanner`.
12. `KnownFilesReader` odczytuje cztery pola znanych plików wybranego folderu
    i zwraca mapę tylko do odczytu. Workflow przekazuje ją do skanera, który
    korzysta z niej do pomijania ciężkiego odczytu przy zgodnych metadanych.
13. `ImageScanType` (prywatny enum w `ImageScanner`) rozróżnia `NEW`,
    `RESCANNED` i `UNCHANGED`. Odrzucone pliki mają osobny licznik.
14. Miniatury i większy podgląd są pobierane po ID rekordu przez
    `GET /api/catalog/files/{id}/thumbnail` oraz `/preview`. Źródła są tylko
    odczytywane. Kontrolujemy ścieżki i limity; PNG powstają w pamięci.
    React pokazuje okrągłe miniatury i jedno drzewko szczegółów pod wybranym
    rzędem. Gałęzie: Plik, Lokalizacja, Historia, Techniczne. Duży obraz jest
    bez przycinania; dane techniczne można rozwinąć. Font retro VT323 jest lokalny.

Hash pliku `demo-images/xzzz1.png`, niezależnie potwierdzony przez PowerShell:

```text
d095274923ae2ae713da857aead939d4878c362d9bad9d39a6258c5c9607bd8d
```

## Aktualna struktura projektu

```text
pl.pum.screensort.scanning
├── workflow      # walidacja → odczyt znanych plików → skan → zapis
├── api           # REST i DTO dla Reacta
├── folder        # walidator folderu, AllowedRoot i wyjątek
├── hashing       # obliczanie SHA-256
├── image         # ImageScanner: przetwarzanie plików
├── result        # ScanResult, ScanSummary, AcceptedImage, RejectedFile, RejectionReason
├── knownfiles    # KnownFile: poprzednio zapisane metadane
├── workers       # konfiguracja puli wątków skanera
└── prototype     # starszy ekran Thymeleaf

pl.pum.screensort.catalog
├── reading       # CatalogReader dla UI, KnownFilesReader dla skanera
├── writing       # CatalogUpdater: zapis i aktualizacja katalogu
├── api           # REST dla Reacta
├── dto           # opis pliku i strony katalogu pokazywanej w UI
├── preview       # odczyt źródła, kontrola pliku, miniatury PNG i ograniczony cache
└── database      # encje i repozytoria JPA
```

Projekt pozostaje modularnym monolitem organizowanym według funkcji produktu.
Nie tworzymy mikroserwisów ani pustych pakietów „na przyszłość”.
Pełniejszy schemat przepływu danych i mapa plików:
[`struktura-projektu.md`](struktura-projektu.md).

## Baza danych i następny etap

Istnieją migracje:

- `V1__initialize_database.sql` — rozpoczęcie historii;
- `V2__create_allowed_root.sql` — tabela zaakceptowanych katalogów źródłowych;
- `V3__create_file_record.sql` — tabela metadanych znalezionych obrazów.

Najbliższy etap:

Encje JPA, repozytoria, zapis/odczyt i testy integracyjne z Testcontainers już
istnieją. Tożsamością pliku w katalogu jest `(allowed_root_id, relative_path)`;
ponowny skan aktualizuje rekord. React pokazuje dane zapisane w bazie.
Skanowanie przyrostowe jest podłączone. Właściciel uzupełnił record
`KnownFile` (`relativePath`, `sizeBytes`, `lastModifiedAt`, `sha256`) w
`scanning/knownfiles` oraz `KnownFilesReader` w `catalog/reading`. Reader jest
komponentem Springa; metoda `read(AllowedRoot)` działa w transakcji tylko do
odczytu. Znajduje folder po canonicalPath, a następnie pobiera `List<KnownFile>`
przez `FileRecordRepository.findAllByAllowedRoot_Id(Long rootId)`. To projekcja
czterech pól, bez ładowania pełnych encji plików i bez bajtów obrazów.
Reader buduje HashMap `relativePath -> KnownFile` i zwraca niemodyfikowalną
migawkę przez `Map.copyOf`. Brak folderu lub plików w bazie oznacza pustą mapę;
błąd bazy nie jest ukrywany jako brak danych. Test `KnownFilesReaderJpaTest`
sprawdza te przypadki, ograniczenie odczytu do jednego folderu oraz brak
możliwości modyfikacji mapy, korzystając z tymczasowego PostgreSQL-a.

`ScanWorkflow` już wykonuje walidację folderu, odczytuje mapę przez readera
i wywołuje `ImageScanner.scan(AllowedRoot, Map<String, KnownFile>)`, a następnie
zapisuje wynik. Skaner nie ma już walidatora w konstruktorze i nie sprawdza
folderu ponownie. Właściciel usunął wcześniejsze zbiorcze pomiary czasu
skanowania i zapisu z workflow; pomiary wewnątrz `ImageScanner` pozostają.

Skaner przekazuje folder i mapę do `processFile`. Worker oblicza ścieżkę
względną i wyszukuje `KnownFile`. Po tanich kontrolach odczytuje aktualny
rozmiar i datę modyfikacji. Gdy obie wartości pasują, używa zapisanego hasha
bez `ImageIO.read` i bez ponownego hashowania. Nowe i niezgodne pliki przechodzą
pełne przetwarzanie. `FileHasher` czyta bajty porcjami z buforem 64 KiB.
Zapis oraz porównanie daty modyfikacji obcinają czas do mikrosekund,
zgodnie z precyzją PostgreSQL-a; `AcceptedImage` zachowuje czas odczytany z dysku.

Worker zwraca obraz z `ImageScanType` w `FileOutcome.Accepted`. Główny wątek
odbierający wyniki buduje `ScanSummary(newImages, rescannedImages,
unchangedImages, rejectedFiles)`. Podsumowanie trafia przez `ScanResultDto`
do Reacta i komponentu `frontend/src/scanning/ScanSummary.tsx`.
Nie zapisujemy liczników w bazie ani nie tworzymy migracji dla tej zmiany.

Rozmiar i data modyfikacji są wskazówką, nie dowodem identycznej zawartości.
`UNCHANGED` oznacza zgodne metadane, nie świeżą weryfikację bajtów. Tryb pełnego
sprawdzenia jest nadal do zaprojektowania. Przyszłe bezpieczne kopiowanie musi
ponownie potwierdzić zawartość źródła.

Galeria, bezpieczny odczyt miniatury po ID oraz większy podgląd są podłączone.
Najbliższy wspólny krok to omówienie tych zmian i wybór kolejnego etapu,
np. postępu długiego skanu. OCR, propozycje nazw,
przegląd i kopiowanie są nadal przed nami. Nie implementuj kolejnej funkcji
za właściciela bez jego wyraźnej prośby. Tauri nie uruchamia jeszcze automatycznie
Javy i nie ma zweryfikowanego instalatora; Docker pozostaje narzędziem developerskim.

Stronicowanie katalogu jest podłączone od PostgreSQL-a do Reacta:

- `CatalogPageDto` opisuje stronę przez `files`, `pageNumber` (od 0), `pageSize`,
  `totalFiles` i `hasNext`; lista jest kopią tylko do odczytu.
- `FileRecordRepository` ma przeciążoną metodę
  `findAllByAllowedRootOrderByRelativePathAsc(AllowedRootEntity, Pageable)`
  zwracającą `Page<FileRecordEntity>`. Ograniczenie strony działa w bazie.
- `CatalogReader.readPage` waliduje folder, tworzy `PageRequest` i mapuje tylko
  pobrane rekordy na DTO. Brak folderu w bazie oznacza pustą stronę.
- Kontroler udostępnia `GET /api/catalog/page`; numer musi być >= 0, rozmiar
  od 1 do 50. Nieprawidłowe wartości zwracają HTTP 400.
- `App.tsx` pobiera jedną stronę po 50 rekordów. Numer żądanej strony jest
  osobnym stanem, a odpowiedź `CatalogPage` zawiera pliki i metadane strony.
  Przyciski zmieniają numer; po udanym skanie wracamy do strony 0. Cleanup
  efektu ignoruje odpowiedzi wcześniejszych żądań.

Stary endpoint `GET /api/catalog/files` pozostaje dostępny, ale React już go
nie używa. Wynik skanu i mapa z `KnownFilesReader` nadal obejmują cały skanowany
folder; nie ograniczamy ich do strony UI. Ta zmiana nie wymaga migracji.

Poprzednia weryfikacja paginacji: dwie strony po jednym pliku, pusta strona
i błędne parametry; ręczne przejście w obie strony oraz blokady na granicach.
Po sprawdzeniu przywrócono 50 rekordów na stronę.

Galeria ma osobną funkcję `catalog/preview`: `CatalogImageSourceReader` czyta
JPA w krótkiej transakcji, `CatalogImagePreview` sprawdza źródło i tworzy PNG,
`PreviewCache` trzyma maksymalnie 128 wpisów / 16 MiB zakodowanych obrazów.
Miniatura ma bok do 320 px, większy podgląd do 1024 px, bez powiększania małych
źródeł. Limit źródła to 50 MiB / 40 mln pikseli / 20 000 px na bok; maksymalnie
2 dekodery naraz. Cache nie pomija walidacji i nie dowodzi zgodności bajtów.
Backend domyślnie nasłuchuje na `127.0.0.1`. To lokalny etap developerski,
nie gotowy model autoryzacji dla publicznej dystrybucji.

Frontend jest rozdzielony na `catalog/types.ts`, `api.ts`, `format.ts`,
`CatalogGallery`, `CatalogImage` oraz `ImageDetailsTree`. `selectedId` wybiera
opis z bieżącej strony; nie kopiujemy całego DTO do nowego stanu. Wybranie
folderu otwiera zapisany katalog bez skanu, a po sukcesie skanu wracamy do
strony 0. Rust i natywny picker nie zostały zmienione. Tymczasowe serwery QA
nie zastępują działającej aplikacji właściciela: po aktualizacji należy
zrestartować Springa, aby nowe endpointy były zarejestrowane.

Aktualny kierunek UI został wybrany przez właściciela: „Gałęzie pod miniaturą”,
okrągłe miniatury, retro PC i cienkie połączenia, zamiast bocznego panelu.
Ponowne kliknięcie zwija drzewko; Escape i krzyżyk przywracają fokus miniaturze.
Native `<details>` rozdzielają metadane na cztery gałęzie. `useGalleryColumns`
i `galleryLayout.selectedRowEnd` umieszczają drzewko po ostatniej miniaturze
rzędu; `useTreeConnections` przez ResizeObserver dopasowuje SVG po zmianie
rozmiaru okna albo rozwinięciu gałęzi. Nie dodano biblioteki grafów ani animacji.
Font jest w `frontend/src/assets/fonts`; licencja w `frontend/public/VT323-OFL.txt`
trafia także do produkcyjnego `dist`. Nie ma zapytań do zewnętrznych fontów.

Dokładny przepływ, mapa nowych plików, limity i ograniczenia:
[`galeria-i-podglady.md`](galeria-i-podglady.md). W ręcznej kontroli galerii
korzystano z istniejących dwóch rekordów demo, bez nowego skanu i bez zmian
danych użytkownika. Sprawdzono prawdziwe miniatury, przełączanie podglądu,
dane techniczne, Escape i szerokości 320/360/1024 px.

Weryfikacja końcowa 2026-10-06: Maven `clean verify` — 68 testów,
66 zaliczonych, 2 pominięte (ręczny benchmark i brak uprawnień do utworzenia
linku symbolicznego w Windows). Build i lint Reacta — poprawne. Konsola
przeglądarki bez błędów w sprawdzonym przepływie. Tymczasowe serwery QA
zatrzymano; procesów właściciela na portach 8080 i 5173 nie zatrzymywano.

Weryfikacja UI 2026-10-07: frontend build, lint i 6/6 testów układu poprawne.
Ręcznie sprawdzono istniejące dwa obrazy demo, wybór/zamknięcie, Enter,
Escape z fokusem, dane techniczne i szerokości 320/360/1024/1280 px.
Bez nowego skanu, zmian oryginałów i zapisu rekordów. Backend i Rust bez zmian;
pełnych testów Maven oraz natywnego okna Tauri nie powtarzano w tym etapie.
Testy frontendu: `cd frontend`, potem `npm.cmd run test`; bez nowego frameworka.

Wykrywanie tej samej zawartości pod różnymi ścieżkami
na podstawie `sha256` jest nadal osobnym, przyszłym zadaniem.

Nie ustawiamy `UNIQUE` na samym `sha256`, ponieważ dwa oddzielne pliki mogą mieć
identyczną zawartość. Szczegółowe uzasadnienia znajdują się w
[`decyzje-techniczne.md`](decyzje-techniczne.md).

## Ustalony kierunek architektury

- `ScreenSortApplication` tylko uruchamia Springa; nie zawiera całego workflow.
- Kontroler tłumaczy HTTP na wywołanie funkcji aplikacji.
- Skaner odpowiada za odczyt katalogu, klasyfikację i metadane.
- Warstwa katalogu odpowiada za zapis i odczyt z bazy.
- Repozytorium bazodanowe nie odczytuje plików z dysku.
- Encje bazy nie powinny być bezpośrednio zwracane do Reacta.
- Tauri zapewnia natywny wybór folderu, ale wybrana ścieżka nadal przechodzi
  przez `ScanFolderValidator`.
- Tylko przyszła funkcja `organization` może zapisywać pliki wyjściowe.

## Bezwzględne zasady bezpieczeństwa

- Wybrany katalog źródłowy jest tylko do odczytu.
- Nigdy nie zmieniaj nazwy, zawartości ani położenia pliku źródłowego i nigdy
  go nie usuwaj.
- Nie skanuj korzenia dysku, całego katalogu domowego ani katalogów systemowych.
- Nie podążaj za linkami systemu plików poza dozwolony katalog.
- Przyszłe kopiowanie wymaga jawnej zgody i nie może nadpisywać plików.
- Kopia jest uznana za poprawną dopiero po porównaniu SHA-256.
- Jeśli nie da się potwierdzić bezpieczeństwa, zatrzymaj operację bez zmian na
  dysku użytkownika.

Przed zmianą zachowania przeczytaj:

- `README.md`;
- `docs/project-brief.md`;
- `docs/architecture.md`;
- `docs/roadmap.md`;
- `docs/decyzje-techniczne.md`;
- `docs/adr/ADR-001-copy-first.md`;
- `docs/adr/ADR-002-react-desktop-ui.md`;
- `docs/adr/ADR-003-tauri-desktop-shell.md`.

ADR-003 zastępuje wybór JavaFX z ADR-002. ADR-002 zostaje w repozytorium jako
historyczny zapis decyzji.

## Uruchomienie na laptopie

Wymagane są Git, Java 25, Docker Desktop z WSL2/Linux containers, Node.js oraz
IntelliJ IDEA.

```bash
git clone https://github.com/Pum2A/screensort.git
cd screensort
git status
./mvnw clean verify
```

Backend z lokalnym PostgreSQL:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend w drugim terminalu:

```bash
cd frontend
npm install
npm run dev
```

Przed zmianą urządzenia wykonaj `git status`, zacommituj kompletną pracę i użyj
`git push`. Na drugim urządzeniu przed edycją użyj `git pull --ff-only`.
Nie edytuj tych samych niezacommitowanych plików niezależnie na dwóch komputerach.

## Prompt do nowej rozmowy AI

```text
Kontynuuj pomaganie mi w budowie ScreenSort z repozytorium otwartego w workspace.

Najpierw przeczytaj README.md, docs/device-handoff.md,
docs/struktura-projektu.md, docs/decyzje-techniczne.md,
docs/project-brief.md, docs/architecture.md,
docs/roadmap.md oraz trzy ADR-y. Sprawdź git status i wszystkie lokalne zmiany.
Kanoniczne prywatne repozytorium to https://github.com/Pum2A/screensort, branch main.

Rozmawiaj ze mną po polsku. Znam programowanie i frontend, ale uczę się Javy,
Springa, SQL-a i backendu. Wyjaśniaj istotne decyzje i składnię, używaj analogii
frontendowych, pracuj małymi krokami. Chcę rozumieć i pisać główny kod samemu,
chyba że wyraźnie proszę o implementację.

Przestrzegaj read-only-source i copy-first. Nigdy nie zmieniaj, przenoś, nazywaj
ani usuwaj źródłowych obrazów. Każdą spójną funkcję lub poprawkę sprawdź,
zacommituj i wypchnij, ale nie dołączaj niepowiązanych lokalnych zmian.

Aktualnie działa walidacja katalogu, accepted/rejected ScanResult, SHA-256,
POST /api/scan, zapis/odczyt katalogu PostgreSQL i GET /api/catalog/page.
React pokazuje wynik skanu, cztery liczniki ScanSummary i zapisane pliki
po 50 na stronę jako siatkę prawdziwych okrągłych miniatur. Kliknięcie rozwija
większy podgląd i połączone gałęzie pod wybranym rzędem: Plik, Lokalizacja,
Historia, Techniczne. Ponowne kliknięcie zwija drzewko; Escape i krzyżyk
przywracają fokus. Lokalny VT323 daje styl retro PC. API liczy strony od 0.
Podglądy są odczytywane po ID przez /api/catalog/files/{id}/thumbnail i /preview.
catalog/preview sprawdza źródło i generuje ograniczone PNG w pamięci, nie zapisuje
ich do źródła ani bazy. Backend domyślnie nasłuchuje tylko na 127.0.0.1.
Działa developerskie okno Tauri + natywny picker; nie JavaFX. ScanWorkflow waliduje folder,
odczytuje mapę przez KnownFilesReader i przekazuje AllowedRoot oraz mapę do
ImageScanner.scan. Worker wyszukuje poprzedni opis po ścieżce względnej.
Po tanich kontrolach porównuje rozmiar i datę modyfikacji; zgodność pozwala
pominąć dekodowanie i użyć poprzedniego SHA-256. ImageScanType rozróżnia
NEW, RESCANNED, UNCHANGED. Czas zapisu/porównania obcinamy do mikrosekund.
Zgodne metadane nie dowodzą identyczności bajtów; przyszłe kopiowanie musi
potwierdzić źródło ponownie. Galeria i podglądy już działają; przeczytaj też
docs/galeria-i-podglady.md. Nie implementuj kolejnej funkcji bez omówienia
jej celu i przepływu danych z właścicielem projektu.
```
