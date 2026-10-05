# ScreenSort — przenoszenie pracy między urządzeniami i rozmowami AI

Ostatnia aktualizacja struktury i bieżącego etapu: 2026-10-05

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
- Docelowo cienka powłoka desktopowa Tauri 2 z małym mostem w Rust.
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
6. `ScanResult` zawiera oddzielne listy `accepted` i `rejected`.
7. `POST /api/scan` przyjmuje JSON z `folderPath`; błędne lub puste żądanie
   zwraca HTTP 400.
8. React wywołuje lokalne API i pokazuje zaakceptowane oraz odrzucone pliki.
9. `ScanWorkflow` przekazuje wynik do `CatalogUpdater`, który zapisuje nowe
   pliki w PostgreSQL albo aktualizuje istniejące rekordy przy kolejnym skanie.
10. `GET /api/catalog/files` przez `CatalogReader` odczytuje zapisane obrazy,
    a React pokazuje je po uruchomieniu oraz po skanowaniu.
11. `FileHasher` jest komponentem Springa i może być wstrzyknięty do
    `ImageScanner`.
12. `KnownFilesReader` odczytuje cztery pola znanych plików wybranego folderu
    i zwraca mapę tylko do odczytu. Nie jest jeszcze podłączony do skanowania.

Hash pliku `demo-images/xzzz1.png`, niezależnie potwierdzony przez PowerShell:

```text
d095274923ae2ae713da857aead939d4878c362d9bad9d39a6258c5c9607bd8d
```

## Aktualna struktura projektu

```text
pl.pum.screensort.scanning
├── workflow      # ScanWorkflow: koordynuje skan i zapis
├── api           # REST i DTO dla Reacta
├── folder        # walidator folderu, AllowedRoot i wyjątek
├── hashing       # obliczanie SHA-256
├── image         # ImageScanner: przetwarzanie plików
├── result        # ScanResult, AcceptedImage, RejectedFile, RejectionReason
├── knownfiles    # KnownFile: poprzednio zapisane metadane
├── workers       # konfiguracja puli wątków skanera
└── prototype     # starszy ekran Thymeleaf

pl.pum.screensort.catalog
├── reading       # CatalogReader dla UI, KnownFilesReader dla skanera
├── writing       # CatalogUpdater: zapis i aktualizacja katalogu
├── api           # REST dla Reacta
├── dto           # opis pliku pokazywany w UI
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
Ustalony następny etap to skanowanie przyrostowe. Właściciel uzupełnił record
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

Reader nie jest jeszcze podłączony do `ScanWorkflow` ani `ImageScanner`.
Obecny skan nadal dekoduje i hashuje również wcześniej poznane pliki.
Następnie wspólnie zmieniamy kolejność workflow: walidacja folderu → odczyt
znanych plików → przekazanie mapy do skanera. Dopiero skaner porówna aktualne
metadane przed ciężkim odczytem obrazu i hashowaniem. Nie implementuj tych
kroków za właściciela bez jego wyraźnej prośby. Rozmiar i data modyfikacji są
wskazówką, nie dowodem identycznej zawartości; potrzebna jest możliwość pełnego
sprawdzenia i uzgodnienie precyzji czasu systemu plików oraz PostgreSQL-a.

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
- Tauri zapewni natywny wybór folderu, ale wybrana ścieżka nadal przechodzi
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
POST /api/scan, zapis/odczyt katalogu PostgreSQL i GET /api/catalog/files.
React pokazuje zarówno wynik skanu, jak i zapisane pliki. Docelowa powłoka
desktopowa to Tauri + cienki most Rust, nie JavaFX. KnownFilesReader już czyta
cztery pola znanych plików do niemodyfikowalnej mapy, ale nie jest podłączony
do workflow/skanera. Następny wspólny krok to podłączenie tego odczytu przed
przetwarzaniem plików. Nie implementuj kolejnej funkcji bez omówienia jej celu
i przepływu danych z właścicielem projektu.
```
