# ScreenSort — przenoszenie pracy między urządzeniami i rozmowami AI

Ostatnia aktualizacja: 2026-09-22

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
9. `FileHasher` jest komponentem Springa i może być wstrzyknięty do
   `ImageScanner`.
10. Ostatnia pełna weryfikacja backendu zakończyła się wynikiem: 9 testów,
    0 błędów, `BUILD SUCCESS`.

Hash pliku `demo-images/xzzz1.png`, niezależnie potwierdzony przez PowerShell:

```text
d095274923ae2ae713da857aead939d4878c362d9bad9d39a6258c5c9607bd8d
```

## Aktualna struktura funkcji skanowania

```text
pl.pum.screensort.scanning
├── safety       # czy wolno skanować wskazany katalog
├── hashing      # obliczanie SHA-256
├── image        # klasyfikacja plików i wynik skanu
└── web          # kontrolery HTTP i DTO dla granicy API
```

Projekt pozostaje modularnym monolitem organizowanym według funkcji produktu.
Nie tworzymy mikroserwisów ani pustych pakietów „na przyszłość”.

## Baza danych i następny etap

Istnieją migracje:

- `V1__initialize_database.sql` — rozpoczęcie historii;
- `V2__create_allowed_root.sql` — tabela zaakceptowanych katalogów źródłowych;
- `V3__create_file_record.sql` — tabela metadanych znalezionych obrazów,
  zweryfikowana na lokalnym PostgreSQL 18.6.

Najbliższy etap:

Encje JPA `AllowedRootEntity` i `FileRecordEntity` istnieją i zostały
zweryfikowane przez Hibernate względem lokalnego PostgreSQL.

1. dodać test integracyjny zapisu i odczytu encji na PostgreSQL Testcontainers;
2. dodać repozytoria Spring Data;
3. zapisywać zaakceptowane obrazy w PostgreSQL;
4. identyfikować ten sam plik przez `(allowed_root_id, relative_path)`;
5. aktualizować istniejący rekord podczas ponownego skanu zamiast dodawać
   kolejną kopię;
6. używać `sha256` do znalezienia tej samej zawartości pod różnymi ścieżkami;
7. dodać test integracyjny: dwa skany tego samego katalogu nadal dają dwa,
   a nie cztery rekordy.

Nie ustawiamy `UNIQUE` na samym `sha256`, ponieważ dwa oddzielne pliki mogą mieć
identyczną zawartość. Szczegółowe uzasadnienia znajdują się w
[`decyzje-techniczne.md`](decyzje-techniczne.md).

## Ustalony kierunek architektury

- `ScreenSortApplication` tylko uruchamia Springa; nie zawiera całego workflow.
- Kontroler tłumaczy HTTP na wywołanie funkcji aplikacji.
- Skaner odpowiada za odczyt katalogu, klasyfikację i metadane.
- Warstwa katalogu będzie odpowiadać za zapis i odczyt z bazy.
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
docs/decyzje-techniczne.md, docs/project-brief.md, docs/architecture.md,
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
POST /api/scan i React połączony z API. Spring ma 9 przechodzących testów.
Docelowa powłoka desktopowa to Tauri + cienki most Rust, nie JavaFX.
Następny etap to tabela file_record i idempotentny katalog PostgreSQL: drugi skan
tego samego katalogu ma aktualizować te same rekordy, a nie tworzyć duplikaty.
```
