# ScanJob: od kliknięcia do wyniku, z objaśnieniem kodu

Stan: 2026-10-07. React korzysta z API zadań w Springu. Nie dodaliśmy Python/FastAPI.
Tauri i Rust pozostają bez zmian; to ta sama Java skanuje i zapisuje katalog.

## Co było już wcześniej, a co dodaliśmy teraz

W poprzednim etapie powstały `ScanStatus`, `ScanJob`, `ScanJobManager`,
`ScanProgressListener`, wykonawca `scanJobRunner` oraz endpointy startu i statusu.
Workflow i skaner dostały callbacki postępu. React nadal wysyłał stary POST,
więc mimo nowego backendu nie pokazywał postępu.

Teraz React wysyła nowy POST, otrzymuje ID, odpytuje status i pokazuje etapy oraz
liczniki. Po zakończeniu pobiera powody odrzucenia i odświeża istniejącą galerię.
Do backendu dopisaliśmy wyłącznie ograniczoną próbkę odrzuconych wpisów i jej GET.
Nie zmienialiśmy algorytmu sprawdzania obrazów, hashera, workerów ani tabel.

## 1. `ScanJob` nadal jest recordem

```java
public record ScanJob(
    UUID id, String folderPath, ScanStatus status, ScanSummary summary, String errorMessage) {
  public ScanJob {
    Objects.requireNonNull(id);
    // pozostałe kontrole wymaganych pól i spójności błędu
  }
}
```

`Objects` to gotowa klasa pomocnicza z `java.util`, nie nowy typ zadania.
`Objects.requireNonNull(id)` znaczy: jeśli ID jest `null`, rzuć wyjątek.
Nie tworzy obiektu, nie zapisuje niczego do bazy i nie zmienia recordu na klasę JPA.
`null` oznacza brak wartości, nie pusty tekst. Dlatego osobno sprawdzamy `isBlank()`.

`public ScanJob { ... }` jest kompaktowym konstruktorem recordu. Nie powtarzamy
parametrów ani przypisań `this.id = id`; Java wykonuje przypisania po jego ciele.
Konstruktor sprawdza dane w chwili tworzenia opisu.

Record jest migawką: status nie ma settera. Manager zastępuje go nowym recordem
z tym samym ID. `UUID` identyfikuje uruchomienie skanu; ID zdjęcia w PostgreSQL
identyfikuje zdjęcie. Drugie kliknięcie oznacza nowe UUID, ale nie nowe ID zdjęć.

## 2. `ScanJobManager.start()` linia po linii

Poniżej jest faktyczny kod, bez wymyślania innego przepływu:

```java
public ScanJob start(Path folder) {
  ScanJob job = reserve(folder);
  try {
    jobRunner.execute(
        () -> {
          try {
            run(job.id(), folder);
          } catch (IOException | RuntimeException exception) {
            // run już ustawił FAILED i zapisał przyczynę w logu.
            // Błąd zadania w tle nie jest odpowiedzią na zakończone żądanie POST.
          }
        });
  } catch (TaskRejectedException exception) {
    fail(job.id(), "Nie można teraz uruchomić skanu. Spróbuj ponownie później.");
    release(job.id());
    throw exception;
  }
  return job;
}
```

1. `public ScanJob start(Path folder)` — metoda dostępna dla kontrolera;
   przyjmuje ścieżkę, zwraca opis zadania, nie gotowe zdjęcia.
2. `reserve(folder)` — sprawdza, czy inny skan już trwa, tworzy UUID i record
   `PREPARING` z zerowymi licznikami, zapisuje go w mapie i zajmuje miejsce.
3. `try` wokół `execute` — obsługuje problem ze **zleceniem** pracy.
4. `jobRunner.execute(...)` — oddaje wykonawcy pracę do uruchomienia w tle.
   To nie `get()`/`join()`; wątek żądania nie czeka na cały skan.
5. `() -> { ... }` — lambda bez argumentów. Jej utworzenie opisuje pracę;
   wykonawca uruchamia jej ciało. Analogicznie do przekazania funkcji w JS,
   ale tutaj wykonawca rzeczywiście wykonuje ją na osobnym wątku Javy.
6. `run(job.id(), folder)` — wykonuje workflow i aktualizuje ten konkretny opis.
7. Wewnętrzny `catch` — błąd pracy **już uruchomionej**. `run()` zapisał `FAILED`
   i log. Nie możemy wysłać nowej odpowiedzi HTTP na zakończony wcześniej POST.
8. Zewnętrzny `catch (TaskRejectedException ...)` — wykonawca nie przyjął pracy.
   Manager zapisuje błąd i zwalnia miejsce. `throw` pozwala kontrolerowi zwrócić 503.
9. `return job` — zwraca początkową migawkę z ID. Wątek w tle może już działać,
   ale ten record pozostaje `PREPARING`. Nowszy stan pobieramy GET-em.

Nie przepisaliśmy tu skanowania. Manager jest obudową istniejącego workflow.

## 3. Reszta managera

| Metoda/pole | Co konkretnie robi |
|---|---|
| `jobs` | `LinkedHashMap<UUID, ScanJob>`: ID → ostatnia migawka. Kolejność wstawienia pozwala usunąć najstarszy opis. To inna mapa niż `HashMap` znanych plików. |
| `activeJobId` | ID zajmujące jedyne miejsce na aktywny skan, albo `null`, kiedy miejsce jest wolne. |
| `reserve` | Sprawdzenie i rezerwacja w jednej krótkiej blokadzie. Maksymalnie 20 opisów w historii; przy 21. usuwa najstarszy opis i jego próbkę odrzuceń. |
| `run` | Wywołuje workflow z callbackiem. Po udanym powrocie wywołuje `complete`; przy błędzie `fail`; `finally` zawsze zwalnia rezerwację. |
| `update` | Wstawia nowy record z tym samym UUID, nowym statusem i licznikami. |
| `complete` | Dzisiejsze rozszerzenie: zapamiętuje najwyżej 100 powodów odrzucenia, następnie ustawia `COMPLETED`. |
| `fail` | Zachowuje ostatnie liczniki, ustawia `FAILED` i komunikat. Szczegóły techniczne błędu SQL zostają w logu. |
| `find` | Odczytuje migawkę z RAM-u. Nie dotyka obrazów ani PostgreSQL. |
| `findRejections` | Odczytuje ograniczoną listę przykładów z RAM-u, dopiero po udanym zakończeniu. |
| `release` | Zwalnia aktywne ID, ale nie usuwa opisu zakończonego zadania. |
| `scanAndSave` | Zachowuje zgodność ze starym synchronicznym API i ekranem Thymeleaf; również blokuje konkurencyjny skan. |

`synchronized` daje jednemu wątkowi naraz dostęp do krótkich operacji na mapach
i rezerwacji. Bez tego dwa żądania mogłyby oba zobaczyć „wolne” i oba wystartować.
Nie trzymamy tej blokady podczas dekodowania, hashowania ani zapisu w bazie.
Limit jednego aktywnego zadania dotyczy **całej aplikacji**, nie tylko jednego folderu.

## 4. `ThreadPoolTaskExecutor` i konfiguracja

To gotowy wykonawca zadań Springa: posiada pulę wątków oraz kolejkę prac.
`ScanJobRunnerConfiguration` tworzy jego ustawienia; sama niczego nie skanuje.

```java
@Bean
public ThreadPoolTaskExecutor scanJobRunner() {
  var executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(1);
  executor.setMaxPoolSize(1);
  executor.setQueueCapacity(1);
  executor.setThreadNamePrefix("scan-job-");
  executor.setStrictEarlyShutdown(true);
  executor.setWaitForTasksToCompleteOnShutdown(false);
  executor.setAwaitTerminationSeconds(5);
  return executor;
}
```

`@Configuration` oznacza klasę ustawień Springa. `@Bean` rejestruje zwrócony obiekt;
Spring inicjalizuje go i zamyka. `var` tylko pozwala kompilatorowi ustalić typ
zmiennej; nie jest dynamicznym typem jak w JavaScript.

Core/max = 1: jeden wątek koordynatora. Queue = 1: jedno techniczne miejsce,
gdy poprzedni koordynator właśnie kończy powrót. Manager nadal odrzuca drugi
aktywny skan, zamiast gromadzić kolejkę folderów. Prefix pomaga odnaleźć wątek
w debuggerze. Trzy ustawienia zamykania ograniczają przyjmowanie nowej pracy
i czekanie przy wyłączaniu; nie gwarantują natychmiastowego przerwania ImageIO.

To **nie** pula przetwarzania zdjęć. Istniejące `scanWorkers` mają 2 wątki,
a `MAX_IN_FLIGHT = 4` oznacza najwyżej 4 zlecone, nieodebrane prace plików.
Koordynator czeka na te workery, dlatego ma własny wykonawca.

`@Qualifier("scanJobRunner")` w konstruktorze managera mówi Springowi,
który konkretny wykonawca ma zostać przekazany.

## 5. Callback, `onProgress`, `Consumer` i `.accept()`

W managerze jest ten fragment:

```java
workflow.scanAndSave(folder, (status, summary) -> update(id, status, summary));
```

Przekazujemy workflow funkcję z dwoma argumentami. Gdy workflow ma wiadomość,
wywołuje ją przez `progress.onProgress(status, summary)`. Funkcja wraca do
managera i podmienia migawkę wskazanego ID. `@FunctionalInterface` oznacza
interfejs z jedną abstrakcyjną metodą, do którego pasuje lambda.

Workflow przekazuje skanerowi prostszy callback:

```java
summary -> progress.onProgress(ScanStatus.SCANNING, summary)
```

Skaner zna tylko nowe liczniki. Workflow dopisuje etap `SCANNING` i przekazuje
wiadomość dalej. W skanerze typ tego callbacka to `Consumer<ScanSummary>`:
gotowy interfejs Javy „przyjmij jedno podsumowanie, nie zwracaj wartości”.

```java
onProgress.accept(new ScanSummary(newImages, rescannedImages, unchangedImages, rejected.size()));
```

`onProgress` jest nazwą zmiennej. `.accept(...)` to wywołanie metody Consumer,
**nie akceptacja obrazu**. Akceptację/odrzucenie nadal opisuje `FileOutcome`.
Ta linia wykonuje się po odebraniu wyniku workera i zwiększeniu liczników.
Nie publikuje liczby dopiero zleconych prac.

Callback nie uruchamia wątku sam z siebie. Wykonuje się na wątku, który go
wywołał. Tutaj koordynator odbiera wyniki workerów i publikuje postęp.
Wywołania bez obserwatora nadal działają przez pusty callback `NONE`.

## 6. Kontroler: co oznacza „bilet” HTTP

```java
return ResponseEntity.accepted()
    .location(URI.create("/api/scan/jobs/" + job.id()))
    .header("Cache-Control", "no-store")
    .body(job);
```

`ResponseEntity` opisuje całą odpowiedź HTTP, nie tylko JSON.
`accepted()` ustawia status 202: „przyjęliśmy pracę”, nie „wszystko już wykonane”.
`location(...)` dodaje nagłówek z adresem do sprawdzania zadania.
`URI.create(...)` tworzy reprezentację adresu, nie otwiera pliku ani strony.
`.body(job)` ustawia record jako treść; Spring serializuje go do JSON.
„Bilet” to analogia do tego ID i adresu — nie kolejna klasa ani encja.

`Cache-Control: no-store` mówi klientom/cache HTTP, by nie przechowywali tej
odpowiedzi. Nie chcemy starego `SCANNING`, gdy zadanie już jest `COMPLETED`.
Nie wyłącza cache znanych plików, nie usuwa historii managera ani bazy.

GET `/api/scan/jobs/{id}` bierze UUID z adresu przez `@PathVariable`, wywołuje
`find` i zwraca 200 albo 404. POST jest potrzebny tylko do **uruchomienia**.
409 oznacza zajęte miejsce, 503 niedostępny wykonawca, 400 błędne żądanie.
Nieistniejący folder przy poprawnej składni ścieżki kończy zadanie jako `FAILED`.

Dzisiejszy GET `/api/scan/jobs/{id}/rejections` daje `files`, `totalRejected`
i `truncated`. Do 100 przykładów, pełna liczba w liczniku; 409 przed ukończeniem,
404 dla utraconego ID. Nie wykonuje drugiego skanu.

## 7. React: nowe pliki i ich zadania

| Plik w `frontend/src/scanning` | Odpowiedzialność |
|---|---|
| `types.ts` | Kształt JSON: ScanJob, ScanStatus, ScanSummaryData, ScanRejections. Typy nie walidują odpowiedzi w runtime. |
| `api.ts` | `startScan` wysyła POST, `fetchScanJob` i `fetchRejections` wysyłają GET. HTTP inne niż udane staje się czytelnym błędem. |
| `polling.ts` | `observeScanJob` powtarza GET dla tego samego ID aż do COMPLETED/FAILED. `waitForPoll` robi przerwę i sprząta timer. |
| `useScanJob.ts` | Hook zarządza stanem Reacta, blokuje podwójny klik, obsługuje przerwanie połączenia, ponowienie odczytu i cleanup po opuszczeniu ekranu. |
| `ScanProgress.tsx` | Widok czterech etapów i sumy sprawdzonych wpisów. Nie skanuje ani nie zapisuje. |
| `ScanSummary.tsx` | Istniejące cztery liczniki; nagłówek rozróżnia zadanie w toku od ukończonego skanu. |

W `App.tsx` stary `handleScan` czekający na pełny wynik został zastąpiony
wywołaniem `scan.start(folderPath)`. Hook dostaje także funkcję wykonywaną
po COMPLETED: zapisuje ostatni folder jako preferencję i wywołuje `showCatalog`.
Istniejące stronicowanie, miniatury, modal i gałęzie szczegółów pozostają.
W `App.css` doszły tylko style etapów, również układ 2 × 2 na wąskim ekranie.

Istotny fragment pollingu:

```typescript
while (!isFinished(job)) {
    await wait(signal)
    signal.throwIfAborted()
    job = await read(initial.id, signal)
    signal.throwIfAborted()
    if (job.id !== initial.id) throw new Error('API zwróciło inne zadanie skanu.')
    onUpdate(job)
}
```

`wait` domyślnie czeka około sekundy. `await read` czeka na bieżący GET,
zanim może zostać zlecony kolejny. Nie ma nakładającego się `setInterval`.
`onUpdate` zapisuje otrzymaną migawkę w stanie Reacta i wywołuje render.
`throwIfAborted` zatrzymuje nieaktualny przepływ; także późny wynik nie trafi do UI.
Parametry `read` i `wait` można podmienić w testach, bez czekania i bez serwera.

POST uruchamiamy z kliknięcia, nie z efektu: StrictMode nie powinien wysłać dwóch
startów. `useRef` przechowuje uchwyt bieżącej operacji i blokuje podwójny klik
jeszcze przed następnym renderem. `useState` przechowuje to, co ma odświeżać widok.
`AbortController` pozwala zakończyć żądania/timer po opuszczeniu ekranu.
To **nie anuluje przyjętego skanu w Javie**.

Po utracie GET-a przycisk „Wznów sprawdzanie zadania” odpytuje to samo UUID,
nie wysyła POST-a. Po błędzie pobrania powodów ponawiamy tylko ten GET.
FAILED nie odświeża katalogu jako udanego skanu. Liczniki opisują przetwarzanie,
nie gwarantują zapisu przy błędzie bazy.

## 8. Uczciwe ograniczenia i sprawdzenie

To obserwowalność skanu, nie kolejna optymalizacja ImageIO/SHA-256.
Nie ma procentów, anulowania ani zapisu zadań w PostgreSQL. Restart Javy traci
opisy; ponowny start strony traci bieżące śledzenie w Reactcie. Katalog pozostaje
w bazie. Nie odtwarzamy automatycznie uruchomionego zadania po restarcie UI.
Po utracie odpowiedzi POST nie ponawiamy go automatycznie, bo skan mógł ruszyć.

Weryfikacja: pełny `mvnw.cmd clean verify` — 93 testy, 91 zaliczonych,
2 dotychczasowe pominięcia (ręczny benchmark oraz link symboliczny w Windows).
Frontend: 20/20 testów, build i lint. Nowe testy obejmują limit odrzuceń,
ich HTTP i brak drugiego skanu, sekwencyjny polling, błędy, przerwanie oraz
odrzucenie odpowiedzi innego ID. Integracja kontroluje SAVING przed commitem.

Ręcznie sprawdzono React → rzeczywiste API Springa → osobny PostgreSQL scanqa:
pierwszy skan demo daje 2 nowe i 1 odrzucony, drugi 2 bez zmian i 1 odrzucony;
nieistniejący folder daje FAILED i nie usuwa galerii. Lokalna baza właściciela
nie była używana do tych testów. Okna Tauri nie testowano w tym etapie.

Dokumentację Reacta i Springa sprawdziliśmy przez skill Context7:
[sprzątanie efektów i zapytań Reacta](https://react.dev/learn/synchronizing-with-effects#fetching-data),
[ResponseEntity](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ResponseEntity.html),
[ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html).
Szerszy opis backendu: [zadania-skanowania.md](zadania-skanowania.md).
