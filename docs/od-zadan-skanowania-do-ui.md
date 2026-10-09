# Od obudowy skanu do obecnego UI — co dokładnie się zmieniło

Stan sprawdzony 2026-10-09. Ten przewodnik dotyczy istniejącego kodu, nie
planowanej architektury. Zacznij od punktów 1–4; nie musisz czytać całego pliku naraz.

## 1. Punkt, w którym się zatrzymałeś

Mieliśmy działające skanowanie i zapis do PostgreSQL. Chciałeś obudować jedno
uruchomienie skanu, aby UI mogło zobaczyć etap i liczniki jeszcze przed końcem.

Przed zmianą:

```text
React → POST /api/scan → wykonaj cały ScanWorkflow → odpowiedź z wynikiem
                        ↑ React czekał do końca tej jednej odpowiedzi
```

Po zmianie:

```text
React → POST /api/scan/jobs → ScanJobManager.start → 202 + ScanJob z ID
                                  │
                                  └→ wykonawca w tle → dotychczasowy ScanWorkflow
                                                         │
                                                    etapy i liczniki
                                                         ↓
React → GET /api/scan/jobs/TO_SAMO_ID ← opis przechowywany przez managera
             powtarzany do COMPLETED albo FAILED

COMPLETED → React odświeża katalog z PostgreSQL i pobiera przykłady odrzuceń
```

Nie stworzyliśmy drugiego algorytmu skanowania. Rozdzieliliśmy „rozpocznij pracę”
od „sprawdź, na jakim etapie jest praca”. Podobnie frontend może osobno wysłać
formularz, a osobno odczytać stan długiego importu.

## 2. Historia: które zmiany rzeczywiście trafiły do projektu

| Commit / etap | Co doszło | Dlaczego |
|---|---|---|
| `755e36d` | `ScanStatus`, `ScanJob`, `ScanJobManager`, osobny wykonawca i powiadomienia o postępie | Uruchomienie w tle i odczytywalny stan jednego skanu. |
| `c91d233` | Start i odczyt zadań w `ScanRestController`; stary prototyp też używa managera | React dostaje HTTP/JSON, a oba wejścia mają wspólną blokadę skanu. |
| `24fd292` | Ograniczona próbka odrzuceń i osobny endpoint | Zachowujemy przyczyny bez trzymania pełnych list wyników w historii. |
| `32d3c9b` | Typy, API, polling, hook `useScanJob`, komponent postępu i podłączenie `App.tsx` | React korzysta z nowych zadań, zamiast czekać na stary POST. |
| `f9fa95f`, `3396fd4` | Wyjaśnienia i kontekst dla innego urządzenia | To dokumentacja, nie kolejne funkcje. |
| Zaakceptowane makiety | Pokazanie jasnego kierunku UI poza repozytorium | Sama makieta nie zmieniała aplikacji. |
| 2026-10-09 — obecna zmiana | Jasny UI, szczegóły obok galerii, animowane gałęzie i niezależne demo analizy | Przenosimy zaakceptowany wygląd do rzeczywistych komponentów Reacta. |

W ostatnim etapie nie zmienialiśmy kodu Javy, Rust, migracji ani bazy.
Nie dodaliśmy OCR, modelu wizualnego, Python/FastAPI ani kopiowania plików.

## 3. `ScanStatus`: słownik etapów, nie funkcja wykonująca pracę

Plik: `src/main/java/pl/pum/screensort/scanning/jobs/ScanStatus.java`.

```java
public enum ScanStatus {
  PREPARING,
  SCANNING,
  SAVING,
  COMPLETED,
  FAILED
}
```

`enum` ogranicza możliwe wartości. Nie piszemy przypadkowych tekstów takich jak
`"Scaning"`. `PREPARING` obejmuje walidację folderu i odczyt znanych plików.
`SCANNING` to przetwarzanie wpisów. `SAVING` oznacza zapis wyniku. `COMPLETED`
ustawiamy po udanym powrocie z zapisu. `FAILED` kończy zadanie przy błędzie.

Odrzucony ZIP to wynik kontroli pliku, nie awaria całego zadania.
Skan może mieć odrzucone wpisy i zakończyć się `COMPLETED`.

## 4. `ScanJob`: opis jednego uruchomienia

Plik: `scanning/jobs/ScanJob.java` pod tym samym głównym pakietem Javy.

```java
public record ScanJob(
    UUID id, String folderPath, ScanStatus status,
    ScanSummary summary, String errorMessage) {
  // W rzeczywistym pliku konstruktor sprawdza poprawność tych danych.
}
```

| Pole | Znaczenie |
|---|---|
| `id` | Numer konkretnego uruchomienia, np. dzisiejszego drugiego skanu. |
| `folderPath` | Folder wybrany dla tego zadania; początkowo jeszcze przed walidacją. |
| `status` | Jeden z pięciu etapów. |
| `summary` | Nowe / sprawdzone ponownie / znane bez zmian / odrzucone. |
| `errorMessage` | Komunikat tylko dla `FAILED`; normalnie `null`. |

To nie jest encja JPA. Ten opis nie trafia do tabeli `file_record`.
Konstruktor sprawdza `requireNonNull`, pustą ścieżkę i zgodność komunikatu
z etapem. `Objects.requireNonNull(x)` rzuca wyjątek, jeżeli `x == null`;
nie skanuje plików ani nie otwiera bazy.

Record jest niemodyfikowalny. Żeby zmienić opis, manager tworzy nowy:

```java
ScanJob previous = jobs.get(id);
jobs.put(id, new ScanJob(id, previous.folderPath(), status, summary, null));
```

Pierwsza linia bierze poprzednią migawkę z mapy. Druga pod tym samym kluczem
zapisuje nową migawkę, zachowując ID i folder. Podobna idea w React:
tworzysz nowy obiekt stanu, a nie zmieniasz pola poprzedniego obiektu.

Trzy ID mają różne znaczenia:

```text
ScanJob.id             UUID konkretnego uruchomienia, zmienia się przy nowym skanie
FileRecordEntity.id    Long jednego zapisanego pliku, zostaje przy ponownym skanie
AllowedRootEntity.id   Long zapisanego folderu, wspólny dla należących do niego plików
```

UUID nie jest hashem zdjęcia, uprawnieniem dostępu ani dowodem bezpieczeństwa.

## 5. `ScanJobManager`: rezerwacja, wykonanie i odczyt

Plik: `scanning/jobs/ScanJobManager.java`.

`@Service` rejestruje obiekt zarządzany przez Spring. Konstruktor otrzymuje
`ScanWorkflow` oraz wykonawcę `TaskExecutor`; przypisania `this.x = x`
zachowują te narzędzia w polach obiektu. Nie rozpoczynają skanowania.

### Pola: co przechowuje manager

```java
private final Map<UUID, ScanJob> jobs = new LinkedHashMap<>();
private final Map<UUID, List<RejectedFile>> rejections = new LinkedHashMap<>();
private UUID activeJobId;
```

Pierwsza mapa to `ID zadania → ostatni opis`. Druga to `ID → próbka odrzuceń`.
`LinkedHashMap` zachowuje kolejność dodawania, więc łatwo usunąć najstarszy
opis po osiągnięciu limitu 20. `activeJobId == null` oznacza brak aktywnego skanu.
Nie przechowujemy tu pikseli obrazów ani kopii całego katalogu PostgreSQL.

### `reserve(folder)`: zarezerwuj możliwość pracy

To krótka metoda `synchronized`:

1. Sprawdza, czy argument nie jest `null`.
2. Gdy istnieje `activeJobId`, rzuca `ScanAlreadyRunningException`.
3. Tworzy UUID i `ScanJob` z `PREPARING` oraz zerowymi licznikami.
4. W razie potrzeby usuwa najstarszy opis i jego próbkę odrzuceń.
5. Zapisuje nowy opis do mapy i ustawia aktywne ID.
6. Zwraca utworzony opis.

`synchronized` pozwala jednemu wątkowi naraz wykonać ten fragment dla danego
managera. Dzięki temu dwa jednoczesne kliknięcia nie przejdą obu sprawdzeń
„jest wolne”. Nie trzymamy tej blokady przez całe skanowanie.

Aktualnie blokujemy drugi skan globalnie, nie tylko drugi skan tego samego folderu.

### `start(folder)`: oddaj bilet, wykonaj pracę w tle

Poniżej rzeczywisty rdzeń metody; pominięto tylko komentarze:

```java
ScanJob job = reserve(folder);
try {
  jobRunner.execute(() -> {
    try {
      run(job.id(), folder);
    } catch (IOException | RuntimeException exception) {
      // run już ustawił FAILED i zalogował przyczynę.
    }
  });
} catch (TaskRejectedException exception) {
  fail(job.id(), "Nie można teraz uruchomić skanu. Spróbuj ponownie później.");
  release(job.id());
  throw exception;
}
return job;
```

`reserve` jest zwykłym bezpośrednim wywołaniem. `execute` zleca wykonawcy funkcję.
`() -> { ... }` to lambda bez parametrów: przekazujemy pracę do wykonania później,
zamiast wykonywać ją przy budowaniu argumentu. Frontendowa analogia to
przekazanie callbacku; faktyczne wykonanie na innym wątku zapewnia tutaj executor.

`run(job.id(), folder)` zna ID, które ma aktualizować. Po przyjęciu pracy
`start` nie czeka na wynik workflow, tylko zwraca początkowy `job`.
Może być bardzo krótki skan, który zakończy się jeszcze zanim UI odczyta odpowiedź.

Wewnętrzny `catch` nie zamienia błędu w sukces: `run` już zapisał `FAILED`.
Zakończonego POST-a nie można później zmienić na odpowiedź HTTP z błędem skanu.
Zewnętrzny `catch` obsługuje inną sytuację: wykonawca nie przyjął pracy.
Wtedy zwalniamy rezerwację, a kontroler odda HTTP 503.

### `run`: wykonać dotychczasowe kroki i zapisać końcowy stan

```java
ScanResult result = workflow.scanAndSave(
    folder, (status, summary) -> update(id, status, summary));
complete(id, result);
return result;
```

Drugi argument to funkcja „gdy otrzymasz postęp, zaktualizuj opis tego ID”.
`complete` następuje dopiero po powrocie z workflow i zakończeniu transakcji
`CatalogUpdater`. Nie wystarcza odebranie wszystkich workerów.

W pełnej metodzie `catch` ustawia `FAILED`, loguje błąd i rzuca go dalej.
`finally` wywołuje `release(id)` zarówno przy sukcesie, jak i błędzie.
`release` czyści aktywne ID, aby można było zacząć kolejny skan.

`find(id)` tylko odczytuje mapę i zwraca `Optional<ScanJob>`. Nie uruchamia
`run`, nie skanuje dysku i nie odczytuje tabeli katalogu. `Optional.empty()`
może oznaczać nieznane ID, restart Javy albo usunięcie starego opisu z historii.

## 6. Dwie pule pracy, nie jeden wspólny worek wątków

Plik: `scanning/workers/ScanJobRunnerConfiguration.java`.

```java
@Bean
public ThreadPoolTaskExecutor scanJobRunner() {
  ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(1);
  executor.setMaxPoolSize(1);
  executor.setQueueCapacity(1);
  executor.setThreadNamePrefix("scan-job-");
  // Dalej konfiguracja zamykania wykonawcy; zobacz rzeczywisty plik.
  return executor;
}
```

`@Configuration` oznacza klasę konfiguracji. `@Bean` każe Springowi zachować
zwrócony obiekt jako dostępne narzędzie i zarządzać jego cyklem życia.
`@Qualifier("scanJobRunner")` w konstruktorze managera wskazuje dokładnie tego
wykonawcę, a nie pulę workerów ani inny executor aplikacji.

`core=1`, `max=1` to jeden wykonujący wątek koordynatora. Kolejka ma jedno miejsce
na przekazanie pracy, gdy poprzedni wątek kończy powrót; manager nie pozwala
użytkownikowi kolejkować wielu aktywnych skanów. Przy wyłączaniu aplikacji
executor odrzuca nową pracę i ma ograniczone oczekiwanie na zakończenie.
To nie jest trwała kolejka pozwalająca kontynuować skan po restarcie.

```text
wątek HTTP: przyjmij POST i oddaj bilet
  ↓
scanJobRunner: jeden koordynator wykonuje ScanWorkflow
  ↓
scanWorkers: obecna osobna pula, 2 workery przetwarzające pliki
             skaner ogranicza liczbę zadań w toku do 4
```

Koordynator czeka na wyniki workerów. Gdyby zajmował ich własną, zbyt małą pulę,
mógłby zabrać miejsce potrzebne do wykonania pracy, na którą czeka.
Nowy executor nie przyspiesza automatycznie ImageIO ani SHA-256; uwalnia
od długiego czekania obsługę żądania HTTP.
API wykonawcy: [Spring — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html).

## 7. `ScanWorkflow` i `Consumer`: jak postęp wraca do managera

Nowy `ScanProgressListener` ma jedną metodę:

```java
void onProgress(ScanStatus status, ScanSummary summary);
```

`@FunctionalInterface` pozwala przekazać implementację lambdą. `NONE` jest
pustą implementacją dla starego wywołania, które nie potrzebuje postępu.
Nie jest to mikroserwis, broker zdarzeń ani funkcja tworząca nowy wątek.

W rzeczywistym workflow kolejność pozostała taka sama:

```java
progress.onProgress(ScanStatus.PREPARING, emptySummary);
AllowedRoot allowedRoot = folderValidator.validate(folder);
Map<String, KnownFile> knowFilesByPath = knownFilesReader.read(allowedRoot);
progress.onProgress(ScanStatus.SCANNING, emptySummary);
ScanResult result = imageScanner.scan(
    allowedRoot, knowFilesByPath,
    summary -> progress.onProgress(ScanStatus.SCANNING, summary));
progress.onProgress(ScanStatus.SAVING, result.summary());
catalogUpdater.update(result.allowedRoot(), result);
return result;
```

`validate` przygotowuje zweryfikowany folder. `read` przygotowuje mapę
poprzednich opisów. `scan` przetwarza pliki, a `update` zapisuje wynik.
Dopisane `onProgress` powiadamiają o tych krokach; nie zastępują ich.

`ImageScanner.scan` otrzymał dodatkowy `Consumer<ScanSummary> onProgress`.
`Consumer<T>` to funkcja przyjmująca `T` i niczego nie zwracająca. Wywołanie:

```java
onProgress.accept(new ScanSummary(
    newImages, rescannedImages, unchangedImages, rejected.size()));
```

znaczy „przekaż aktualne liczniki do otrzymanego callbacku”. Słowo `accept`
jest nazwą metody interfejsu Consumer — tutaj nie oznacza akceptacji zdjęcia!
Callback jest wywoływany po odebraniu gotowego wyniku. Tylko koordynator
zwiększa wspólne liczniki; workery nie aktualizują ich równocześnie.

Nie znamy wcześniej liczby wszystkich wpisów bez dodatkowego ich policzenia.
Dlatego pokazujemy etap i sprawdzone wpisy, a nie zmyślony procent.

## 8. `ScanRestController`: granica HTTP

To istniejący kontroler z nowymi metodami. Najważniejszy fragment:

```java
ScanJob job = scanJobs.start(Path.of(request.folderPath()));
return ResponseEntity.accepted()
    .location(URI.create("/api/scan/jobs/" + job.id()))
    .header("Cache-Control", "no-store")
    .body(job);
```

| Linia / adnotacja | Co oznacza |
|---|---|
| `@PostMapping("/api/scan/jobs")` | Ten URL przyjmuje żądanie rozpoczęcia pracy. |
| `@RequestBody ScanRequest` | Spring odczytuje JSON, np. `{ "folderPath": "demo-images" }`. |
| `@Valid` | Sprawdza reguły danych żądania; to nie pełna walidacja dysku. |
| `Path.of(...)` | Zamienia tekst na obiekt ścieżki. Samo to nie dowodzi, że folder jest bezpieczny. |
| `scanJobs.start(...)` | Rezerwuje i zleca skan. |
| `accepted()` | Odpowiedź HTTP 202: przyjęto pracę, nie „już zakończono ją poprawnie”. |
| `location(...)` | Nagłówek z adresem odczytu tego zadania. Nie przekierowuje automatycznie Reacta. |
| `header(..., "no-store")` | Cache nie powinien zachowywać odpowiedzi ze zmiennym stanem zadania. |
| `body(job)` | Spring zamieni opis na JSON odpowiedzi. |

`no-store` nie usuwa map managera, nie blokuje zapisu katalogu w PostgreSQL
i nie anuluje skanu. Dotyczy odpowiedzi HTTP.
[Spring — ResponseEntity](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ResponseEntity.html),
[Spring — CacheControl](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/CacheControl.html).

`GET /api/scan/jobs/{id}`: `@PathVariable UUID id` bierze ID z URL.
`find` daje opis albo brak. `.map(...)` zamienia znaleziony opis na odpowiedź
200. `.orElseGet(...)` buduje 404, jeśli opis nie istnieje. To `Optional.map`,
nie mapowanie listy plików i nie mapa `HashMap`.

Przy błędach: niepoprawne żądanie/format ścieżki → 400, drugi aktywny skan → 409,
wykonawca nie przyjął pracy → 503. Błąd walidacji folderu już uruchomionego
zadania pojawia się później jako `FAILED`, nie jako zmiana wysłanego 202.
Stare `POST /api/scan` i ekran Thymeleaf pozostały synchroniczne, ale używają managera.

## 9. Powody odrzuceń: osobny odczyt, ograniczona pamięć

Po sukcesie manager przechowuje najwyżej 100 `RejectedFile` z wyniku.
Endpoint `GET /api/scan/jobs/{id}/rejections` zwraca `ScanRejectionsDto`:
próbkę `files`, pełny licznik `totalRejected` i `truncated`.

Jeżeli odrzucono 900 wpisów, UI pokazuje „100 przykładów z 900”, nie udaje pełnej
listy. Przed `COMPLETED` endpoint oddaje 409, po utracie zadania 404.
Nie trzyma pełnego `ScanResult` dla wszystkich 20 zadań i nie ponawia skanowania.

## 10. React: nowe pliki i rzeczywista odpowiedzialność każdego

| Plik w `frontend/src/scanning` | Rola |
|---|---|
| `types.ts` | Typy JSON: ScanJob, ScanStatus, podsumowanie i odrzucenia. TypeScript nie waliduje automatycznie JSON w runtime. |
| `api.ts` | Jeden POST startu, GET stanu i GET odrzuceń; parametry, AbortSignal, no-store, czytelne błędy HTTP. |
| `polling.ts` | Odczekaj, odczytaj ten sam identyfikator, opublikuj opis; zatrzymaj się na końcu lub błędzie. |
| `useScanJob.ts` | Stan Reacta oraz start, wznowienie obserwacji, próba pobrania odrzuceń i cleanup. |
| `ScanProgress.tsx` | Etap, liczba sprawdzonych wpisów, ID do rozwinięcia; nie wykonuje skanu. |
| `ScanSummary.tsx` | Wyświetla cztery liczniki tego zadania. Nie jest liczbą całego katalogu. |

W `App.tsx` kliknięcie robi `scan.start(folderPath)`. Callback przekazany do
`useScanJob` po `COMPLETED` zapamiętuje folder i wywołuje `showCatalog`.
To powoduje zwykły GET strony zapisanych opisów; nie pobieramy katalogu z ScanJob.

### Polling bez nakładających się żądań

Rzeczywisty środek `observeScanJob`:

```typescript
let job = initial
onUpdate(job)
while (!isFinished(job)) {
    await wait(signal)
    signal.throwIfAborted()
    job = await read(initial.id, signal)
    signal.throwIfAborted()
    if (job.id !== initial.id) throw new Error('API zwróciło inne zadanie skanu.')
    onUpdate(job)
}
return job
```

`initial` to bilet z POST-a. `onUpdate` to tutaj `setJob`. `wait` domyślnie
czeka 1000 ms, a `read` domyślnie wywołuje `fetchScanJob`. Następny GET zaczyna
się dopiero po zakończeniu poprzedniego i kolejnej przerwie, więc „co sekundę”
jest przybliżeniem, a nie precyzyjnym zegarem. Nie ma `setInterval` ani kolejnego POST-a.

Krótki skan może przejść przez `SCANNING` i `SAVING` między dwoma GET-ami.
Wtedy UI zobaczy od razu `COMPLETED`; nie oznacza to pominięcia etapów w Javie.

### Hook: stan, blokada kliknięć i przerwanie obserwowania

`useState` trzyma dane potrzebne do renderowania: job, błąd, stan żądania i
odrzucenia. `useRef` trzyma `AbortController` bez dodatkowego renderowania.
Ref pozwala od razu zablokować drugi start, jeszcze zanim React odświeży przycisk.

```typescript
useEffect(() => () => { operation.current?.abort() }, [])
```

Pierwsza funkcja jest efektem. Funkcja, którą zwraca, jest cleanupem przy
odmontowaniu komponentu. Przerywa żądania i oczekiwanie UI. Nie zatrzymuje
przyjętego zadania Javy — to wymagałoby osobnego API anulowania.
Zasada cleanup: [React — synchronizing with effects](https://react.dev/learn/synchronizing-with-effects).

`resume()` wznawia GET-y tego samego ID. Nie rozpoczyna nowego skanu.
Jeżeli POST utracił odpowiedź, ale Java już przyjęła pracę, hook nie wysyła
automatycznie ponownego POST-a. `FAILED` jest wynikiem końcowym, a nie powodem
do automatycznego restartu. Restart UI traci bieżące śledzenie; restart Javy
traci mapy zadań, ale nie zapisane rekordy katalogu.

## 11. Testy dodane w etapie zadań — nie tylko zielony kolor

`ScanJobTest` sprawdza poprawność recordu. `ScanJobManagerTest` sprawdza między
innymi oddanie biletu przed wykonaniem workflow, blokadę drugiego skanu,
postęp, błędy, zwolnienie blokady i ograniczenie historii/próbki odrzuceń.
Testy workflow i skanera sprawdzają kolejność powiadomień oraz liczniki po
odebraniu wyników. `ScanningComponentContextTest` sprawdza połączenie beanów Springa.

`ScanJobControllerTest` sprawdza HTTP i treść odpowiedzi. `ScanJobApiIntegrationTest`
obejmuje rzeczywisty zapis z użyciem tymczasowego PostgreSQL-a z Testcontainers.
Testy frontendu sprawdzają pojedynczy POST, kolejne GET-y tego samego ID,
błędy HTTP, zakończenie, wznowienie i przerwanie obserwacji.

Weryfikacja backendu z 2026-10-07: 93 testy, 91 zaliczonych, 2 wcześniejsze
pominięcia. To wcześniejszy wynik; 2026-10-09 nie uruchamialiśmy ponownie Maven,
ponieważ bieżąca zmiana nie dotyka Javy.

## 12. Co doszło teraz: rzeczywisty interfejs, nie backend analizy

```text
App.tsx
  ├─ useScanJob → prawdziwe API zadań (bez zmian w tym etapie)
  ├─ fetchCatalog → prawdziwa strona opisów z PostgreSQL (bez nowego endpointu)
  └─ CatalogGallery
       ├─ siatka okrągłych CatalogImage → istniejący endpoint miniatur
       ├─ wybrany plik → CatalogDetailsPanel → pola bieżącego CatalogFile
       │                  └─ Powiększ → ImageDetailsDialog → endpoint /preview
       └─ osobny tryb DEMO → AnalysisDemo → examples.ts, nie dane wybranego zdjęcia
                              ├─ AnimatedBranch: opis wizualny / tekst / historia
                              └─ ReviewActions: propozycja i decyzja tylko w React
```

### Pliki i zmiany — bez szukania „co jest nowe?”

| Plik | Co zmieniłem i dlaczego |
|---|---|
| `App.tsx` | Dodałem `analysisDemoOpen` i przycisk DEMO. Galeria ma wspólną obudowę również przy ładowaniu, braku plików i błędzie, więc demo nie potrzebuje działającej bazy. |
| `catalog/CatalogGallery.tsx` | Wybranie kółka pokazuje boczny panel, nie od razu modal. Oddzielny stan otwiera powiększenie. Na wąskim ekranie panel trafia pod galerię i przewijamy do niego po wyborze. |
| `catalog/CatalogDetailsPanel.tsx` — nowy | Wyświetla prawdziwe dane DTO: Plik, Lokalizacja, Historia i Techniczne. Nie dorabia opisu OCR dla rzeczywistego zdjęcia. |
| `ui/AnimatedBranch.tsx` — nowy | Wspólny komponent rozwijanej gałęzi z płynnym otwarciem i zamknięciem oraz obsługą dostępności. |
| `analysis/demo/examples.ts` — nowy | Trzy jawnie fikcyjne przykłady: osoba, terminal i notatki. Nie mają ID rekordu katalogu ani folderu źródłowego. |
| `analysis/demo/AnalysisDemo.tsx` — nowy | Wybór przykładu, opis, tekst, tagi, kategoria i lokalne decyzje. Nie wywołuje OCR ani API. |
| `analysis/demo/ReviewActions.tsx` — nowy | Edycja propozycji i „Zatwierdź (demo)”. Zmiana tylko lokalnego stanu; zamknięcie demo lub przeładowanie usuwa decyzje. |
| `analysis/demo/reviewName.ts` — nowy | Wskazówki formularza: pusta nazwa, znaki ścieżki, końcówka, popularne nazwy zarezerwowane w Windows. To nie produkcyjna walidacja kopiowania. |
| `catalog/CatalogImage.tsx` | Spinner podczas faktycznego ładowania; zachowane lazy loading miniatur, asynchroniczne dekodowanie i fallback błędu. |
| `catalog/ImageDetailsTree.tsx` | Te same dane w większym podglądzie, ale gałęzie korzystają też ze wspólnego AnimatedBranch. |
| `catalog/ImageDetailsDialog.tsx` | Zachowany X/Escape/tło i blokada modalu. Tab pomija zwinięte, nieaktywne gałęzie; fokus wraca do przycisku Powiększ. |
| `ui/Icon.tsx` | Kilka ikon w istniejącym zestawie SVG; bez nowej biblioteki. |
| `index.css`, `App.css` | Jasne kolory, wspólne parametry animacji, rozmiar kółka 116 px i zaokrąglenia. |
| Style komponentów | Nowe `.module.css` dla nowych komponentów; dotychczasowy zbiorczy `CatalogGallery.css` rozdzielony na style obrazu, dialogu i drzewa. |
| `tests/analysisDemo.test.ts`, `tsconfig.tests.json` | Osiem dodatkowych testów przykładów i wskazówek formularza, przez obecny test runner Node. |

Stary zbiorczy CSS usunięto dopiero po przeniesieniu potrzebnych reguł. Jest
odzyskiwalny z Git; nie usunięto źródłowych zdjęć ani starszego repozytorium.

### Jak działa animowana gałąź

```typescript
const [open, setOpen] = useState(defaultOpen)
const id = useId()
```

`open` to stan tylko tej jednej gałęzi. `useId` tworzy powiązane identyfikatory
nagłówka i treści dla czytnika ekranu; nie jest ID zdjęcia ani zadania.
Przycisk wywołuje zmianę stanu. React zapisuje `aria-expanded` i `data-open`.

```css
.panel { display: grid; grid-template-rows: 0fr; opacity: 0; }
.panel[data-open="true"] { grid-template-rows: 1fr; opacity: 1; }
```

W pełnym CSS jest transition wysokości układu i przezroczystości z tokenem
360 ms. Treść pozostaje w DOM podczas zwijania: gdybyśmy usunęli ją od razu
przez `{open && ...}`, nie byłoby czego płynnie zamykać. `inert={!open}` i
`aria-hidden={!open}` wyłączają zamkniętą treść dla klawiatury/czytnika.
Przy preferencji ograniczenia ruchu animacje są wyłączone.

To lokalny stan Reacta i CSS, nie zadanie na workerze i nie postęp skanu.
Dokumentacja: [React — useId](https://react.dev/reference/react/useId).

### Dlaczego CSS Modules, a nie migracja całego projektu na Tailwind

```typescript
import styles from './CatalogDetailsPanel.module.css'
// W JSX: className={styles.panel}
```

Vite obsługuje taki plik bez dodatkowej paczki. Nadaje klasom zakres komponentu:
`panel` analizy nie koliduje z `panel` szczegółów. Globalny CSS zostaje dla
kolorów, fontu, wspólnych przycisków i ramy aplikacji.
To rozdzielenie odpowiedzialności, a nie twierdzenie, że CSS jest stary.
[Vite — CSS Modules](https://vite.dev/guide/features#css-modules).

Tailwind jest możliwą alternatywą, ale sama zamiana klas nie przyspieszy
ImageIO, zapytań JPA ani skanu. Tutaj nie instalowaliśmy nowych zależności.
Nie animujemy ciągle kosztownego rozmycia; hover używa transformacji,
a gałęzie krótkiej zmiany układu tylko przy interakcji.

## 13. Co sprawdzono teraz i czego jeszcze nie mamy

2026-10-09: frontend test 28/28, build i lint poprawne. W osobnym podglądzie
z fikcyjnym API sprawdzono wybór pliku, powiększenie, Escape/powrót fokusu,
drugą stronę katalogu, błędny katalog, trzy przykłady demo, niepoprawną propozycję
i lokalne zatwierdzenie. Gałąź pozostaje w DOM, ma 360 ms przejścia i jest
nieaktywna po zamknięciu. Kontrola szerokości 320/360/1024 px nie wykazała
poziomego wyjeżdżania treści. Rzeczywisty frontend przeszedł też sekwencję
POST/GET do testowego API, kończąc na COMPLETED i odświeżeniu katalogu.

To kontrola UI, nie nowy test integracyjny Javy ani dowód działania OCR.
Nie uruchamialiśmy natywnego okna Tauri, nie wykonywaliśmy prawdziwego skanu,
nie zmienialiśmy lokalnej bazy ani oryginałów. Tymczasowe narzędzia QA są
poza repozytorium i nie zastępują API aplikacji.

Analiza rzeczywistych zdjęć, trwałe propozycje, ręczne zatwierdzanie prawdziwych
wyników i tworzenie zweryfikowanych kopii nadal wymagają backendu.
Nie podłączamy tego za właściciela bez jego zgody.

## 14. Jak odzyskać kontrolę nad kodem

Czytaj w kolejności: `ScanJob` → `ScanJobManager.start` → `run` →
`ScanWorkflow.scanAndSave(..., progress)` → `onProgress.accept` w skanerze →
`ScanRestController.startJob/findJob` → `observeScanJob` → `useScanJob.track` →
`App.tsx`. Dla wyglądu: `CatalogGallery` → `CatalogDetailsPanel` → `AnimatedBranch`.
Demo to osobna ścieżka `analysis/demo`, nie dalszy krok skanera.

Pytanie kontrolne dla dowolnej metody: co dostaje, co wykonuje, co oddaje
i kto ją wywołuje? Nie musisz wymyślać API executora z pamięci. Ważne, żebyś
potrafił prześledzić tę drogę i uzasadnić potrzebę każdego etapu.
