# ScreenSort — przenoszenie pracy między urządzeniami i rozmowami AI

Ostatnia aktualizacja bieżącego etapu i UI: 2026-10-15

## Prezentacja portfolio i LinkedIn — 2026-10-15

Po uwadze właściciela zastąpiono główny pokaz slajdów w README dwiema pętlami
`feature-explore` i `feature-preview`, po 2,5 s, w GIF i MP4. To serie rzeczywistych
klatek z montażowym kursorem, kadrowaniem i przejściami, nie nowe funkcje UI.
Stary 49-sekundowy materiał zachowano jako dodatkowy. Opisy LinkedIn przepisano
na krótką, produktową narrację techniczną; instrukcje redakcyjne nie są częścią
tekstu do wklejenia. Niczego nie opublikowano na LinkedIn, repo jest nadal prywatne.

Przebudowano główny README jako prezentację istniejącej wersji przedpremierowej.
Instrukcje developerskie są w `docs/development.md`, gotowe teksty po polsku
w `docs/linkedin-projekt.md`, a opis pochodzenia mediów w `docs/presentation/README.md`.
`docs/media` zawiera rzeczywiste zrzuty interfejsu, diagram SVG, GIF i krótki MP4.

Media powstały w przeglądarkowej sesji React z prawdziwą Javą, osobną bazą PostgreSQL
i lokalnym Pythonem. Dane wejściowe były syntetyczne, bez prywatnych zdjęć.
Skan: 6 nowych + 1 odrzucenie, reskan: 6 niezmienionych + 1 odrzucenie.
OCR i Florence zwróciły prawdziwe wyniki zapisane przez Javę. Film to opisany
montaż ujęć i plansz, nie ciągłe nagranie ani benchmark. Nie zmieniano backendu.

Repo pozostaje prywatne. Nie opublikowano LinkedIn ani instalatora jako publicznego
wydania. Nazwy, kategorie, kopiowanie i pobieranie pakietu AI na żądanie są nadal
planami, nie wdrożonymi funkcjami. Nie dopisuj ich do listy gotowych możliwości.

## Poprawka instalatora 0.1.1 — rzeczywisty start Tauri

Właściciel zgłosił błąd inicjalizacji PostgreSQL po normalnym starcie 0.1.0
i polecił go naprawić. `initdb` nie obsługiwał ścieżki `//?/C:/...`, powstałej
z prefiksu `\\?\` zwracanego przez resolver Tauri. Pliki serwera były kompletne.
Odtworzono błąd na osobnych danych; nie zmieniano bazy właściciela.

Dodano bezpieczną normalizację runtime/data na granicy procesów (dunce 1.0.5),
sześć testów natywnych i `--startup-check`, który używa rzeczywistego kontekstu
Tauri zamiast ręcznego `--runtime-dir`. Nie tworzy okna/WebView, nie używa bazy
użytkownika. Skrypt budowania wymaga jego sukcesu przed utworzeniem NSIS.
Pełny test przeszedł: rzeczywisty resolver zwrócił prefiks, a baza, skan,
OCR/wizja, miniatura i odczyt wyniku po restarcie zadziałały. Nowy program
przeszedł również test z wymuszonym prefiksem i runtime'em starej instalacji.
Nie przeprowadzano jeszcze aktualizacji w profilu właściciela ani testu klików UI.
Wersja 0.1.1 zachowuje identyfikator i dane aplikacji. Nie zalecać kasowania
hasła, katalogu danych ani starego repo. Patrz [opis poprawki](naprawa-startu-windows.md).

## Instalator Windows i analiza end-to-end — 2026-10-15

To aktualny etap; wcześniejsze sekcje są datowaną historią. Właściciel jawnie
wybrał **instalator .exe bez narzędzi developerskich**, nie tylko jeden start dev.
Zezwolił przywrócić zaakceptowany ImageAnalyzer z niezależnymi wynikami.

Dodano zabezpieczony POST Pythona z opcjami i surowymi bajtami, readiness,
pojedynczy worker modeli i ograniczenia uploadu/czasu. Java ma moduł `analysis`
(bezpieczny odczyt po ID, klient, osobne zadania, typowany kontrakt i zapis JPA).
Flyway V4 tworzy `file_analysis`. React pokazuje prawdziwe wyniki wybranego pliku,
nie tylko dotychczasowe, nadal oznaczone demo.

Rust nadzoruje prywatne PostgreSQL, Javę i Pythona. Adresy są losowe na loopback,
tokeny pozostają po stronie natywnej. Desktop używa mostu IPC z allowlistą API;
przeglądarka dev nadal używa proxy Vite. Modele są dołączone i pracują na CPU.
PostgreSQL desktop jest oddzielny od Dockera; nie nadpisano developerskich danych
ani oryginałów. Nowy instalator zaczyna z własnym pustym katalogiem.

Budowanie: `scripts/build-windows.ps1`; runtime lock i notices w `packaging/windows`.
Duże pliki runtime/modeli/instalatora są ignorowane przez Git. Skrypt zachowuje
poprzedni wygenerowany runtime w `.release/backups`. Do budowy na laptopie potrzeba
toolchainu i pobranego modelu, do uruchomienia gotowego instalatora — nie.

Dokładny opis po polsku: [instalator i przepływ danych](instalator-windows.md).
Decyzja i alternatywy: [ADR-005](adr/ADR-005-private-windows-runtimes.md).
Nie dodawaj teraz automatycznego kopiowania, zmiany nazw, GPU ani kolejnych
mikroserwisów bez uzgodnienia. Następne zadanie to omówienie zmian z właścicielem
i audyt działającego pakietu, nie kolejne tysiące linii bez pokazania mu przepływu.

Weryfikacja kodu: 104 testy Pythona (także realne OCR + Florence offline),
93 testy Javy bez Dockera, 31 testów frontendu, natywny test allowlisty.
Lint Pythona/frontendu i kompilacja są poprawne. Testy Testcontainers nie zostały
uruchomione: Docker Desktop był wyłączony. Osobny test pakietu używa prawdziwego,
dołączonego PostgreSQL i modeli na generowanym obrazie; patrz aktualny raport
w przewodniku instalatora. Nie traktuj samej kompilacji jako dowodu testu UI,
czystej VM ani jakości opisów modelu.

## Niezależne wyniki OCR i wizji — 2026-10-15

Właściciel obejrzał i zaakceptował kod tego kroku. `ImageAnalyzer.analyze`
przygotowuje obraz raz i zachowuje niezależne wyniki OCR/wizji. Każda część
ma `status`, `result`, `error`: `COMPLETED` zawiera dane, `FAILED` bezpieczny
kod/komunikat, `SKIPPED` oznacza niezamówiony krok. Domyślnie działają oba.
Flagi `run_ocr` i `run_visual` muszą być podane po nazwie; obie wyłączone
przerywają wywołanie przed przygotowaniem. Nie zmieniono konstrukcji silników.

Zwykły wyjątek jednego modelu nie kasuje udanej części i nie blokuje drugiej.
Pusty tekst OCR to sukces. Błędy obrazu, MemoryError i KeyboardInterrupt
nadal przerywają pracę; obraz jest zamykany. Nie logujemy treści wyjątków
ani prywatnych danych. To nie mechanizm odzyskiwania po awarii procesu.

Jawna zmiana JSON: `ocr.result.text`, `visual.result.description`,
`ocr.result.duration_ms` i `visual.result.duration_ms`. Obie części wyniku
są wymagane, zamiast dawnego `visual=None`. Typowane warianty Pydantic
odrzucają sprzeczne kombinacje. Minimalne Pydantic to 2.11 dla składni
generycznej Pythona 3.12+; lock zachował dotychczasowe wersje pakietów/modeli.

Weryfikacja 2026-10-15: 88/88 testów, w tym prawdziwy OCR i Florence offline
na syntetycznym obrazie; Ruff lint/format i kontrola lockfile poprawne.
Testy nie czytają prywatnych zdjęć. Nie zmieniono Javy, Reacta, bazy ani
oryginałów; nie dodano HTTP analizy. `/health` nadal ma analysis_available=false.

Przeczytaj [kod krok po kroku](analiza-obrazu-krok-po-kroku.md) i
[README serwisu](../analysis-service/README.md). Najbliższy krok do omówienia:
zabezpieczony POST z ograniczonymi bajtami, opcjami, tokenem, limitem uploadu
przed parsowaniem, readiness i ograniczeniem pracy w toku; dopiero potem
klient Javy i zapis JPA. Nie implementuj następnego etapu bez nowej zgody.
Poniżej pozostaje datowana historia wcześniejszego zachowania.

## Rzeczywisty Florence CPU i JSON całej analizy — 2026-10-12

Florence działa lokalnie razem z OCR. Zainstalowano opcjonalny zestaw `vision`:
PyTorch 2.14.1+cpu z oficjalnego indeksu CPU, Transformers 5.19.0 i Hub 2.2.0,
we własnym `.venv`. `vision/model.py` wspólnie definiuje ID i przypięty revision.
`vision/download_model.py` jawnie pobiera wybrane dane modelu do ignorowanego
`models/`, bez zdalnego kodu i bez konta. Nie uruchamia analizy ani nie przesyła
zdjęć. Wagi/cache nie trafiają do Git: laptop wymaga instalacji i pobrania.

`image/console.py` otrzymuje jeden wskazany plik, czyta go w ograniczeniu 50 MiB,
ustawia tryb offline i tworzy OCR + VisionAnalyzer raz. Pokazuje JSON z hashem,
tekstem, opisem, wersjami i czasami pierwszego/kolejnego przebiegu. Rzeczywisty
opis jest teraz krótki, po angielsku, `<CAPTION>`. Nie ma jeszcze kategorii,
proponowanych nazw, tłumaczenia ani oceny trafności na całym zbiorze.
Sprawdzono odczyt wcześniej udostępnionej fotografii: hash, rozmiar i mtime
źródła nie zmieniły się. Jej treść i prywatny OCR nie zostały zapisane w repo.

Weryfikacja: 47/47 testów z włączonym realnym OCR i Florence, także pełna analiza
syntetycznego obrazu z zablokowaną siecią. Lint i formatowanie poprawne.
Testy integracyjne są opt-in; podstawowy zestaw nie wymaga modelu wizji.
To nie benchmark katalogu ani pełna ocena jakości opisów.

Ponowna weryfikacja 2026-10-14 przed zapisem etapu w Git: 47/47 testów,
również prawdziwy OCR i Florence offline; lint i formatowanie nadal poprawne.

Przeczytaj [raport i następne kroki](florence-pierwsze-uruchomienie.md) oraz
[polecenia w README](../analysis-service/README.md). HTTP nadal ma tylko health
z `analysis_available: false`: modele nie są jeszcze wpięte do serwera HTTP.
Nie zmieniono Javy, Reacta, bazy ani źródeł. Następny etap do uzgodnienia:
częściowe wyniki, zabezpieczony POST Pythona, klient Javy i osobna tabela/encja
analizy JPA. Nie łączyć Pythona z bazą katalogu i nie robić z ScanJob zadania
analizy. Kolejnego backendu nie implementować bez omówienia z właścicielem.
Poniższe sekcje opisują wcześniejsze etapy tego samego dnia i historię.

## Wspólne przygotowanie obrazu i samodzielny OCR — 2026-10-12

Właściciel dodał RapidOCR 3.10.0, ONNX Runtime i Pillow, opcje/wynik OCR,
`VisualResult` i kandydata Florence w `vision/analyzer.py`. Na jego wyraźną
prośbę wydzielono `image/preparation.py` i dodano `image/analyzer.py`.
Odczyt PNG/JPEG, kontrola bajtów/wymiarów i RGB są wspólne. Konstruktor
`ImageAnalyzer(ocr, vision)` otrzymuje wcześniej utworzone silniki. `analyze`
przygotowuje raz, hashuje oryginalne bajty, następnie wywołuje kolejno OCR i wizję.
`ImageAnalysisResult.visual` jest opcjonalne dla starszych przykładów OCR-only.

`OcrRecognizer.recognize(bytes, options)` nadal działa w konsoli;
nowe `recognize_prepared(image, options)` pożycza już przygotowany obraz.
Właściciel obrazu zamyka go przez `contextlib.closing`; pomocnicze bufory też
są zamykane. Błędy nie są zamieniane na udane puste wyniki. OCR duration
nie zawiera już przygotowania; pierwszy przebieg może zawierać lazy loading.
Nie interpretować tych czasów jako porównania całego starego/nowego pipeline.

Rzeczywisty OCR sprawdzono przed i po refaktorze na syntetycznym obrazie:
`ScreenSort OCR test` oraz `Order number 12345`, poprawny wynik obu wywołań.
Test integracyjny jest opt-in i blokuje sieć. Jednostkowe testy koordynatora
korzystają ze sztucznej wizji, nie są dowodem trafności Florence.
Nie zainstalowano torch/transformers, nie pobrano modelu wizualnego, nie ma
realnej inferencji wizji, GPU ani połączenia z Javą/Reactem. HTTP nadal tylko
health z `analysis_available: false`; nie dodano endpointu analizy.

Weryfikacja refaktoru: 40/40 testów Pythona z włączonym rzeczywistym OCR,
Ruff lint i formatowanie poprawne, uv.lock zgodny; wheel i sdist zbudowane
offline. Testy health i narzędzia uv wymagały uruchomienia poza ograniczonym
sandboxem Windows. Nie zmieniano Javy, Reacta, bazy ani źródłowych obrazów.

Przeczytaj [kod i wyjaśnienia](analiza-obrazu-krok-po-kroku.md) oraz
[README serwisu](../analysis-service/README.md). Następna propozycja do omówienia:
uzgodniona instalacja runtime wizji i lokalny cache przypiętego modelu,
potem próba na jednym obrazie. Przed API potrzebne są limity uploadu,
autoryzacja, readiness, ograniczona praca w toku i niezależne błędy częściowe.
Nie dopisuj tego bez kolejnej zgody właściciela. Starsze sekcje poniżej
pozostają datowanym zapisem wcześniejszych etapów.

## Przyjęty lokalny serwis analizy — 2026-10-11

Właściciel zaakceptował osobny lokalny proces Pythona, z Javą jako rdzeniem.
Dodano `analysis-service` w tym samym repozytorium, bez nowego `.git`:
pakiet `screensort_analysis`, FastAPI, aplikacja-fabryka `main.create_app`,
`api/health.py`, `api/schemas.py`, testy i własne `pyproject.toml` + `uv.lock`.
W development nasłuchuje na `127.0.0.1:8001`, jeden proces. Nie uruchamia się
automatycznie z Tauri. Zależności instalujemy tylko we własnym `.venv` przez uv.

`GET /health` odpowiada `service: screensort-analysis`, `status: ok`,
`analysis_available: false`. To liveness, nie gotowe modele. Endpointy `/ready`
i `/api/v1/analyses` nie istnieją. Nie podłączono klienta Javy, Reacta, OCR,
wizji, GPU, kolejki, autoryzacji ani zapisu wyników. Nie pobierano modeli,
nie zmieniano źródeł i bazy. Nie myl tego z wcześniejszym benchmarkiem ONNX.

Java będzie właścicielem zadań analizy, bezpieczeństwa źródła i trwałych wyników.
Python ma analizować ograniczone bajty obrazu i zwracać częściowe rezultaty,
hash rzeczywistego wejścia, wersje modeli i błędy. Bez wspólnej bazy, encji JPA,
ścieżek systemowych z klienta i bez operacji na oryginałach. React rozmawia
z Javą, nie bezpośrednio z Pythonem. NVIDIA/CUDA jest przyszłą opcją,
nie wdrożoną funkcją i nie zamiennikiem działającego wariantu CPU.

[ADR-004](adr/ADR-004-local-python-analysis-service.md) zastępuje wcześniejszy
plan odłożenia osobnego procesu analizy do czasu po MVP. Nie zmienia ADR-001.
Nie rozbijamy skanowania na mikroserwisy i nie tworzymy pustych warstw na zapas.
Pierwszy wspólny krok: kontrakt jednej analizy oraz zabezpieczenia przed
implementacją OCR. Właściciel chce nadal sam pisać istotny backend;
nie implementuj modeli, klienta i kolejnych funkcji bez nowej zgody.

Uruchomienie i objaśnienia: [`analysis-service/README.md`](../analysis-service/README.md).
Weryfikacja 2026-10-11: Python 3.14.4, uv 0.11.25, FastAPI 0.143.0,
Uvicorn 0.54.0, Pydantic 2.14.0. Pięć testów przeszło również z `-W error`,
bez ostrzeżeń; lint i kontrola formatowania Ruff poprawne. Zbudowano wheel
i sdist. Fabryka uruchomiona przez Uvicorn odpowiedziała HTTP 200 na rzeczywisty
GET `/health` na losowym porcie loopback; tymczasowy serwer zatrzymano.
Windows sandbox blokował launchery i izolował połączenia: kontrolę wykonano
poza nim, bez zmiany kodu na obejścia środowiska. W aktualnej wersji Starlette
TestClient korzysta z `httpx2`; nie ukrywano ostrzeżenia starego `httpx`.
Maven, frontend i natywne Tauri nie były zmieniane ani ponownie testowane.

## Eksperyment wyboru języka analizy — 2026-10-11

Dodano odizolowany `benchmarks/vision-bindings`, nie serwis produkcyjny.
Porównano Python 3.14.4, Java 25 i Rust 1.95.0 na jednym modelu MobileNet v2
oraz identycznej natywnej DLL ONNX Runtime 1.26.0. CPU, 30 procesów,
6000 mierzonych wywołań: przy 1 wątku około 8,1–8,3 ms, przy 4 około
3,1–3,4 ms (zbiorcze mediany, zmienność opisana w raporcie).
Zweryfikowano zgodność ostatniego wyniku każdego procesu. Wejścia syntetyczne;
to nie test OCR, trafności kategorii, generowania opisów ani szybkości całej apki.

Wykryto AMD Radeon RX 6600; nie testowano GPU. Nie zakładać CUDA dla AMD.
Python pozostaje rekomendowanym kandydatem do lokalnego procesu analizy ze
względu na integrację modeli, nie udowodnionym zwycięzcą wydajności.
W samym eksperymencie nie dodano FastAPI, OCR/modeli do aplikacji ani nowych
zależności głównego backendu. Nie modyfikowano bazy i zdjęć. Późniejszy szkielet
serwisu i przyjęcie ADR-004 opisuje aktualna sekcja powyżej.

Przeczytaj [`benchmark-analiza-jezyki.md`](benchmark-analiza-jezyki.md).
Następny proponowany krok: mały, opisany zbiór obrazów i ocena kandydatów OCR
oraz modeli wizualnych w Pythonie. Nie implementuj backendu analizy bez zgody.

## Bieżąca zmiana UI i zasady dalszej pracy

Właściciel zatwierdził jasny układ galerii i panelu obok. Kliknięcie kółka
pokazuje `CatalogDetailsPanel` z rzeczywistymi polami DTO, a „Powiększ” otwiera
zachowany centralny dialog. Wspólny `ui/AnimatedBranch` animuje obie strony
rozwijania, używa `aria-expanded`, `inert` i respektuje reduced motion.

`frontend/src/analysis/demo` zawiera wyłącznie trzy jawnie fikcyjne przykłady.
Nie przypisuj ich do rzeczywistych zdjęć. Zatwierdzanie propozycji zmienia tylko
stan Reacta; zamknięcie demo/przeładowanie go usuwa. Nie ma OCR, analizy wizualnej,
zapisu propozycji w PostgreSQL ani operacji na oryginałach. Backend analizy chce
napisać właściciel — nie implementuj go bez nowej zgody.

Nowe style używają CSS Modules, bez instalacji Tailwind ani biblioteki animacji.
Stary zbiorczy CSS galerii rozdzielono na obraz, dialog i drzewo. Jawne globalne
tokeny w `index.css`: jasna paleta, 360 ms ruchu i kółka 116 px. Rust, API
i logika Javy nie zmieniły się w tym etapie.

Najpierw przeczytaj [`od-zadan-skanowania-do-ui.md`](od-zadan-skanowania-do-ui.md):
historia od ScanJob, fragmenty rzeczywistego kodu, objaśnienia i wykaz dzisiejszych zmian.
To uzupełnia starsze przewodniki, w których sam klik kółka otwierał modal.

Weryfikacja 2026-10-09: frontend test 28/28, build i lint poprawne. Ręczna kontrola
na odizolowanym, fikcyjnym API: szczegóły/zoom/Escape/fokus, strony, błąd katalogu,
trzy przykłady demo, formularz, gałęzie oraz szerokości 320/360/1024 px.
Kontrola POST/GET zadań zakończyła się COMPLETED i odświeżeniem katalogu w tym
testowym API. To nie powtórzenie integracyjnych testów backendu. Maven i natywnego
Tauri w tym etapie nie uruchamiano; lokalna baza i zdjęcia właściciela bez zmian.

Poniższe datowane wyniki testów pozostają zapisem wcześniejszych etapów.

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
- Python + FastAPI: API tylko health; samodzielny OCR i Florence CPU
  w `analysis-service`, bez integracji z Javą/Reactem i bez GPU.
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
React pokazuje okrągłe miniatury, boczne szczegóły oraz powiększony podgląd nad przyciemnionym,
    lekko rozmytym tłem. Gałęzie: Plik, Lokalizacja, Historia, Techniczne. Duży obraz jest
    bez przycinania; dane techniczne można rozwinąć. Font retro VT323 jest lokalny.
15. Backend ma zadania skanu: `POST /api/scan/jobs` zwraca 202 + ID, a
    `GET /api/scan/jobs/{id}` aktualny status i `ScanSummary`. Jeden aktywny skan
    w całej aplikacji; drugi start zwraca 409. Historia do 20 opisów jest tylko
    w pamięci, bez migracji. `COMPLETED` dopiero po udanym zapisie w bazie.
    React korzysta z nowego POST-a i odpytuje status około raz na sekundę.
    Pokazuje etapy i liczniki, po COMPLETED odświeża katalog, po FAILED pokazuje błąd.
    GET `/api/scan/jobs/{id}/rejections` zwraca do 100 przykładów, total i truncated.
    Oba stare kontrolery również korzystają z blokady managera.

Hash pliku `demo-images/xzzz1.png`, niezależnie potwierdzony przez PowerShell:

```text
d095274923ae2ae713da857aead939d4878c362d9bad9d39a6258c5c9607bd8d
```

## Aktualna struktura projektu

```text
pl.pum.screensort.scanning
├── workflow      # walidacja → odczyt znanych plików → skan → zapis
├── jobs          # ScanJob, ScanStatus, manager i blokada jednego aktywnego skanu
├── api           # REST i DTO dla Reacta
├── folder        # walidator folderu, AllowedRoot i wyjątek
├── hashing       # obliczanie SHA-256
├── image         # ImageScanner: przetwarzanie plików
├── result        # ScanResult, ScanSummary, AcceptedImage, RejectedFile, RejectionReason
├── knownfiles    # KnownFile: poprzednio zapisane metadane
├── workers       # dwa workery plików i osobny wykonawca całego zadania
└── prototype     # starszy ekran Thymeleaf

pl.pum.screensort.catalog
├── reading       # CatalogReader dla UI, KnownFilesReader dla skanera
├── writing       # CatalogUpdater: zapis i aktualizacja katalogu
├── api           # REST dla Reacta
├── dto           # opis pliku i strony katalogu pokazywanej w UI
├── preview       # odczyt źródła, kontrola pliku, miniatury PNG i ograniczony cache
└── database      # encje i repozytoria JPA
```

Java pozostaje modularnym rdzeniem organizowanym według funkcji produktu.
Obok jest lokalny szkielet Pythona opisany w ADR-004. Nie rozbijamy całego
produktu na kolejne mikroserwisy ani nie tworzymy pustych pakietów na zapas.
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
unchangedImages, rejectedFiles)`. Podsumowanie trafia przez `ScanJob`
do Reacta i komponentu `frontend/src/scanning/ScanSummary.tsx`.
Nie zapisujemy liczników w bazie ani nie tworzymy migracji dla tej zmiany.

Rozmiar i data modyfikacji są wskazówką, nie dowodem identycznej zawartości.
`UNCHANGED` oznacza zgodne metadane, nie świeżą weryfikację bajtów. Tryb pełnego
sprawdzenia jest nadal do zaprojektowania. Przyszłe bezpieczne kopiowanie musi
ponownie potwierdzić zawartość źródła.

Galeria, bezpieczny odczyt miniatury po ID oraz większy podgląd są podłączone.
Postęp backendu i React są połączone na wyraźną prośbę właściciela.
Najbliższy wspólny krok to omówienie kodu i wybór kolejnej funkcji produktu.
Przeczytaj [`scanjob-krok-po-kroku.md`](scanjob-krok-po-kroku.md): start managera,
Objects, callbacki, Consumer, wykonawca, nagłówki HTTP oraz nowe pliki Reacta.
Etap zadań skanu nie dodawał Python/FastAPI ani zmian w Rust. Późniejszy szkielet
Pythona opisuje sekcja na początku dokumentu. Odrzucenia są ograniczoną próbką
do 100 na zadanie, nie pełnym wynikiem w historii. GET-y statusu nie skanują.
Po utracie połączenia UI pozwala wznowić GET tego samego ID. Restart UI traci
śledzenie; abort zapytań nie anuluje skanu w Javie.
OCR, propozycje nazw,
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
`CatalogGallery`, `CatalogDetailsPanel`, `CatalogImage`, `ImageDetailsDialog` oraz `ImageDetailsTree`. `selectedId` wybiera
opis z bieżącej strony; nie kopiujemy całego DTO do nowego stanu. Wybranie
folderu otwiera zapisany katalog bez skanu, a po sukcesie skanu wracamy do
strony 0. Rust i natywny picker nie zostały zmienione. Tymczasowe serwery QA
nie zastępują działającej aplikacji właściciela: po aktualizacji należy
zrestartować Springa, aby nowe endpointy były zarejestrowane.

Historia kierunku UI do 2026-10-07: okrągłe miniatury, retro PC i cienkie połączenia.
Na kolejną prośbę właściciela gałęzie przeniesiono spod rzędu do centralnego
podglądu. `ImageDetailsDialog` używa natywnego dialog.showModal(), blokuje
tło i przewijanie galerii. X, Escape i kliknięcie poza oknem przywracają fokus
miniaturze. Tab / Shift+Tab zawijają się wewnątrz; nagłówek z X nie przewija się.
`<details>` rozdzielają metadane na cztery gałęzie. Usunięto `useGalleryColumns`
i `selectedRowEnd`; `useTreeConnections` przez ResizeObserver dopasowuje SVG
do podglądu i gałęzi. Nie dodano biblioteki modali, grafów ani animacji.
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
Ręcznie sprawdzono istniejące dwa obrazy demo, Enter, X / Escape / kliknięcie
tła z powrotem fokusu, Tab / Shift+Tab, przeciągnięcie z obrazu na tło,
dane techniczne, przewijanie oraz szerokości 320/360/1024/1280 px i wysokość 500 px.
Bez nowego skanu, zmian oryginałów i zapisu rekordów. Backend i Rust bez zmian;
pełnych testów Maven oraz natywnego okna Tauri nie powtarzano w tym etapie.
Testy frontendu: `cd frontend`, potem `npm.cmd run test`; bez nowego frameworka.
Lint sprawdza jawnie `src`, `tests` i `vite.config.ts`, nie biblioteki ani buildy.

Poprzednia weryfikacja backendu z zadaniami skanu 2026-10-07: Maven `clean verify` —
BUILD SUCCESS, 90 testów, 88 zaliczonych i 2 wcześniejsze pominięcia.
22 nowe testy obejmują start przed wykonaniem workflow, postęp, błędy,
blokadę równoczesnych skanów, limit historii, API i rzeczywisty zapis.
Testcontainers używały tymczasowego PostgreSQL-a 18.6; lokalna baza i źródła
nie były modyfikowane. W tamtym etapie React/Rust były bez zmian.

Weryfikacja integracji Reacta z zadaniami 2026-10-07: Maven `clean verify` —
93 testy, 91 zaliczonych i 2 wcześniejsze pominięcia. Frontend: 20/20 testów,
build i lint poprawne. Ręcznie sprawdzono prawdziwe API i osobny tymczasowy
PostgreSQL scanqa: pierwszy i ponowny skan demo, powód odrzucenia oraz FAILED
nieistniejącego folderu bez usunięcia galerii. Nie używano lokalnej bazy właściciela.
Nowe pliki Reacta są w `frontend/src/scanning`. Rust bez zmian; natywnego
okna Tauri nie uruchamiano. Pełna instrukcja: `docs/scanjob-krok-po-kroku.md`.

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
- `docs/adr/ADR-003-tauri-desktop-shell.md`;
- `docs/adr/ADR-004-local-python-analysis-service.md`;
- `analysis-service/README.md`.

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
docs/roadmap.md oraz cztery ADR-y. Przeczytaj też analysis-service/README.md.
Sprawdź git status i wszystkie lokalne zmiany.
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
Backend ma też POST /api/scan/jobs (202 + ID) i GET /api/scan/jobs/{id}.
ScanJobManager utrzymuje jeden aktywny skan i do 20 opisów w pamięci;
ScanJob to record, ScanStatus enum. Osobny scanJobRunner wykonuje workflow;
ScanProgressListener przenosi status i liczniki, workery nie zmieniają mapy.
COMPLETED dopiero po udanym zapisie, FAILED zachowuje ostatnie liczniki.
Stare API i prototyp również używają blokady managera. React używa nowego API
zadań: jeden POST, potem sekwencyjne GET-y tego samego ID. Pokazuje etapy
i żywe liczniki, po COMPLETED odświeża katalog i pobiera do 100 przykładów
odrzuconych przez GET /api/scan/jobs/{id}/rejections. FAILED pokazuje błąd.
Przeczytaj docs/scanjob-krok-po-kroku.md i docs/zadania-skanowania.md.
Przeczytaj również docs/od-zadan-skanowania-do-ui.md — chronologię od zadań
do obecnego UI z konkretnymi fragmentami kodu i wyjaśnieniem nowej prezentacji.
Restart Javy traci opisy zadań, nie katalog; restart UI traci bieżące śledzenie.
Dodano lokalny Python/FastAPI w analysis-service: HTTP tylko GET /health,
analysis_available: false, własny pakiet, uv.lock i testy. Samodzielny OCR CPU
działa. image/preparation.py przygotowuje obraz; ImageAnalyzer łączy OCR i wizję.
Florence CPU jest sprawdzony z prawdziwym modelem offline. Runtime to opcjonalny
zestaw vision; lokalny cache nie jest w Git. Przeczytaj również
docs/analiza-obrazu-krok-po-kroku.md i docs/florence-pierwsze-uruchomienie.md.
Brak GPU, klienta Javy,
połączenia z Reactem i automatycznego startu z Tauri. Granice: ADR-004.
Java zachowuje zadania i zapis, Python nie dostaje bazy ani dowolnych ścieżek.
OCR/wizja mają już niezależne COMPLETED/FAILED/SKIPPED; dane w ocr.result
i visual.result. Zwykły błąd jednego silnika nie usuwa udanej części.
Pełne testy 2026-10-15: 88/88, w tym rzeczywisty OCR i Florence offline.
Kolejny krok: uzgodnić zabezpieczony HTTP analizy, następnie klienta Javy
i zapis JPA. Nie implementuj tego bez wyraźnej zgody.
React pokazuje wynik skanu, cztery liczniki ScanSummary i zapisane pliki
po 50 na stronę jako siatkę prawdziwych okrągłych miniatur. Kliknięcie pokazuje
CatalogDetailsPanel obok galerii (na telefonie pod nią). Przycisk Powiększ
otwiera centralny podgląd nad rozmytym tłem: Plik, Lokalizacja, Historia,
Techniczne. X, Escape i kliknięcie tła zamykają go i przywracają fokus Powiększ.
Natywny dialog blokuje tło; długie dane przewijają się wewnątrz. Lokalny VT323
daje styl retro PC. API liczy strony od 0. ImageDetailsDialog obsługuje okno,
ImageDetailsTree pokazuje dane. Usunięto stare pozycjonowanie pod rzędem miniatur.
Nowy jasny UI ma wspólne AnimatedBranch z płynnym otwieraniem/zamykaniem,
CSS Modules i reduced motion. analysis/demo zawiera osobne fikcyjne przykłady
opisu wizualnego, tekstu OCR, kategorii/tagów i propozycji nazw; nie są wynikiem
dla wybranego pliku. Zatwierdzenie zmienia tylko pamięć Reacta, nie API,
PostgreSQL ani pliki. Nie implementuj OCR/backendu analizy za właściciela.
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
