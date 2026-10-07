# Zadanie skanowania: co dodaliśmy i jak to działa

Stan: 2026-10-07. Backend i React są połączone przez API zadań.
Objaśnienie kodu startu, callbacków, nagłówków i nowych plików UI:
[ScanJob krok po kroku](scanjob-krok-po-kroku.md).

## 1. Problem, który rozwiązujemy

Dotychczas żądanie HTTP czekało na walidację, odczyt znanych plików, przetworzenie
wszystkich wpisów i zapis do PostgreSQL. React otrzymywał wynik dopiero na końcu.
Samo dopisanie ID do tego wyniku nie pozwoliłoby obserwować skanu w trakcie pracy.

Nowe API oddaje ID od razu po przyjęciu pracy. Workflow wykonuje się w tle,
a osobne żądanie odczytuje aktualny opis jego stanu. Nie przyspieszyliśmy w ten
sposób dekodowania obrazów ani SHA-256; umożliwiliśmy obserwowanie pracy.

```text
POST /api/scan/jobs { folderPath }
  → ScanRestController
  → ScanJobManager.start(folder)
      → rezerwacja jednego aktywnego skanu
      → ScanJob: nowe UUID, PREPARING, zerowe liczniki
      → zlecenie pracy osobnemu scanJobRunner
  ← HTTP 202 + początkowy ScanJob + nagłówek Location

scanJobRunner, w tle:
  → ScanWorkflow
      → PREPARING: walidator + mapa znanych plików z bazy
      → SCANNING: ImageScanner + dotychczasowe workery plików
          → po odebraniu wyniku: nowa migawka ScanSummary
      → SAVING: CatalogUpdater i jego transakcja
  → po udanym powrocie: COMPLETED
  → przy błędzie: FAILED, komunikat i zwolnienie miejsca na kolejny skan

GET /api/scan/jobs/{id}
  → aktualny ScanJob, bez skanowania i bez zapytania do bazy
```

## 2. Gdzie jest jaki kod

Pliki Javy są pod `src/main/java/pl/pum/screensort`.

| Plik | Odpowiedzialność |
|---|---|
| `scanning/jobs/ScanStatus.java` | Enum pięciu etapów całego skanu. |
| `scanning/jobs/ScanJob.java` | Record z ID, wybranym folderem, statusem, licznikami i błędem. Nie wykonuje operacji. |
| `scanning/jobs/ScanJobManager.java` | Rezerwacja, start w tle, odczyt i podmiana migawek; ograniczona historia. |
| `scanning/jobs/ScanAlreadyRunningException.java` | Informacja, że inny skan już trwa; API tłumaczy ją na HTTP 409. |
| `scanning/workers/ScanJobRunnerConfiguration.java` | Osobny wykonawca całego workflow, zarządzany przez Springa. |
| `scanning/workflow/ScanProgressListener.java` | Mały interfejs powiadomienia o etapie i licznikach. |
| `scanning/workflow/ScanWorkflow.java` | Ta sama kolejność: walidacja → znane pliki → skan → zapis. Teraz także zgłasza etap. |
| `scanning/image/ImageScanner.java` | Dotychczasowe sprawdzanie obrazów; po odbiorze każdego wyniku publikuje liczniki. |
| `scanning/api/ScanRestController.java` | Nowe endpointy oraz zgodność ze starym API. |
| `scanning/prototype/ScanController.java` | Prototyp Thymeleaf korzysta z tej samej blokady aktywnego skanu. |

## 3. ScanJob nie jest encją JPA ani gotowym wynikiem

Jedno kliknięcie „Skanuj” oznacza jedno uruchomienie i nowe UUID.
Folder i zdjęcia mogą nadal mieć te same ID w PostgreSQL przy drugim skanie.
UUID zadania identyfikuje wykonanie pracy, nie folder, zdjęcie ani jego zawartość.
Nie jest hashem, uprawnieniem ani mechanizmem autoryzacji.

`ScanJob` ma tylko pięć pól:

- `id`: UUID konkretnego uruchomienia;
- `folderPath`: wybrana ścieżka w postaci tekstu, jeszcze niekoniecznie zweryfikowana;
- `status`: `ScanStatus`;
- `summary`: istniejący niemodyfikowalny `ScanSummary`;
- `errorMessage`: tekst dla `FAILED`, w pozostałych stanach `null`.

Konstruktor pilnuje wymaganych pól i spójności komunikatu błędu.
Record nie ma setterów: zmiana postępu tworzy nowy `ScanJob` z tym samym ID.
Stara odpowiedź HTTP pozostaje starą migawką; GET pobiera nowszą.

`ScanResult` nadal zawiera pełne listy zaakceptowanych i odrzuconych wpisów.
Manager nie przechowuje dodatkowej kopii pełnych list. UI odczytuje zapisane
obrazy przez `GET /api/catalog/page`. Po udanym zakończeniu manager pamięta
do 100 odrzuconych wpisów na zadanie; osobny GET `/api/scan/jobs/{id}/rejections`
zwraca tę próbkę, pełną liczbę odrzuconych i informację o skróceniu listy.
Przy usuwaniu starego opisu usuwa także próbkę. Nie magazynujemy pełnych
wyników skanu w historii 20 zadań.

## 4. Metody managera po kolei

### start(folder)

Najpierw `reserve` tworzy opis i rezerwuje miejsce na skan. Następnie
`jobRunner.execute(...)` przekazuje wykonanie do osobnego wątku. Metoda zwraca
początkowy opis bez `get()`, `join()` ani czekania na zapis w bazie.
Mała lambda `() -> ...` opisuje pracę do wykonania później; samo jej utworzenie
nie wykonuje workflow. Jeśli wykonawca nie przyjmie pracy, zapisujemy `FAILED`,
zwalniamy rezerwację i API zwraca 503.

### reserve(folder)

`activeJobId` mówi, że miejsce na skan jest zajęte. Sprawdzenie i rezerwacja są
w jednej metodzie `synchronized`, więc dwa równoczesne kliknięcia nie przejdą
jednocześnie sprawdzenia. Blokada dotyczy całej aplikacji, także innych folderów.
Obejmuje nowe zadania oraz oba stare wejścia webowe.

`LinkedHashMap` zachowuje kolejność utworzenia opisów, dzięki czemu możemy usunąć
najstarszy przy rozpoczęciu 21. zadania. Maksymalnie pamiętamy 20 opisów, razem
z aktywnym. Usunięcie opisu zadania nie usuwa rekordów PostgreSQL ani plików.

### run(id, folder)

Wywołuje workflow z funkcją przekazującą powiadomienia do `update`.
Po udanym powrocie `complete` zapamiętuje próbkę odrzuceń i ustawia `COMPLETED`.
`CatalogUpdater` jest oddzielnym beanem
z transakcją, która kończy się przed powrotem do workflow. Błąd zapisu lub
commitowania powoduje `FAILED`, a nie fałszywe zakończenie.

Przy `IOException` albo błędzie wykonania zachowujemy ostatnie liczniki.
Błąd walidatora folderu może trafić do użytkownika bezpośrednio; inne przyczyny
są logowane w backendzie, a odpowiedź ma ogólny komunikat bez szczegółów SQL.
`finally` zwalnia rezerwację także przy błędzie.

### update / fail / find

`update` i `fail` zastępują opis nowym recordem. `find` zwraca bieżącą migawkę
albo `Optional.empty()`. Nie odczytuje folderu ani bazy.
Te metody również są `synchronized`: wątek HTTP nie czyta mapy podczas jej
modyfikowania. Nie blokujemy w ten sposób całego długiego workflow — monitor
jest zajęty tylko na krótką zmianę lub odczyt opisu.

### scanAndSave(folder)

To zgodność ze starym API i prototypem. Rezerwuje ten sam zasób, ale
wykonuje workflow w wątku żądania i zwraca stary `ScanResult`. Dlatego stare UI
działa bez postępu. React używa już nowego API. Stare wejście nie używa
dodatkowego zadania w tle.

## 5. Co zmieniło się we workflow i skanerze

`ScanProgressListener` ma jedną metodę:

```java
void onProgress(ScanStatus status, ScanSummary summary);
```

To callback: kod workflow wywołuje przekazaną funkcję, kiedy ma nową informację.
Nie jest nowym wątkiem, mikroserwisem ani zdarzeniem w Kafka.
`NONE` jest funkcją, która nic nie robi — pozwala zachować stare wywołania.

Workflow publikuje przygotowanie przed walidacją, skanowanie po odczycie mapy
oraz zapisywanie przed updaterem. Skaner otrzymuje `Consumer<ScanSummary>`:
funkcję przyjmującą podsumowanie i niczego nie zwracającą. Właściwa linia jest
po odbiorze `finished.get()` i po zwiększeniu odpowiedniego licznika.

Workery nie modyfikują mapy zadań ani współdzielonych list. Wątek koordynujący
odbiera wyniki i publikuje postęp. Przetworzone wpisy to suma czterech liczników;
obejmują także odrzucone wpisy, nie tylko poprawne obrazy. Te liczniki opisują
przetwarzanie, nie gwarantują jeszcze zapisu: przy błędzie bazy zadanie może mieć
dodatnie liczniki i status FAILED bez zapisania nowych rekordów.
Nie pokazujemy procentu,
bo nie liczymy całego folderu dodatkowym przebiegiem. Pusty folder ma zera.

Oryginalne warianty `ImageScanner.scan(root, knownFiles)` i
`ScanWorkflow.scanAndSave(folder)` nadal istnieją i używają pustego callbacka.
Reguły bezpieczeństwa, szybka ścieżka znanych plików i hash nie zostały zmienione.

## 6. Dlaczego osobny wykonawca

Obecne `scanWorkers` mają dwa wątki przetwarzające pliki. Skaner utrzymuje
maksymalnie cztery zlecone, jeszcze nieodebrane zadania plików.
`scanJobRunner` ma jeden wątek koordynujący całe uruchomienie i jedno miejsce
w kolejce. Manager blokuje następny aktywny skan, więc nie budujemy kolejki
folderów. Pojedyncze miejsce obsługuje krótki moment, gdy poprzednie zadanie
zwolniło rezerwację, a jego wątek właśnie kończy powrót.

Nie uruchamiamy workflow w puli jego własnych workerów: czeka na ich wyniki,
więc zajmowałby im miejsce. Nie zmieniamy liczby workerów ani algorytmów.
Spring inicjalizuje i zamyka wykonawcę. Przy zamykaniu nie przyjmujemy nowej
pracy; przerwanie bieżącej jest najlepszą próbą, nie gwarancją natychmiastowego
przerwania każdego odczytu obrazu.

Wybraliśmy jawne `execute` zamiast `@Async`, żeby pokazać miejsce zlecenia pracy
i nie opierać nauki tego etapu na dodatkowych proxy oraz wywołaniach adnotacji.
Nie uruchamiamy niezarządzanego `new Thread` dla każdego kliknięcia.

Dokumentacja sprawdzona przez Context7:
[ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
oraz [ResponseEntity](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ResponseEntity.html).

## 7. API i ręczna obserwacja

| Operacja | Odpowiedź |
|---|---|
| POST `/api/scan/jobs` z `folderPath` | 202, opis początkowy z ID oraz `Location` do odczytu |
| GET `/api/scan/jobs/{id}` | 200, aktualny opis; `Cache-Control: no-store` |
| GET `/api/scan/jobs/{id}/rejections` | Po COMPLETED: 200, do 100 przykładów, total i truncated; przed końcem 409, utracone ID 404 |
| Drugi start, kiedy skan trwa | 409, bez nowego skanu |
| Wykonawca nie może przyjąć pracy | 503, bez pozostawienia zajętej rezerwacji |
| Puste żądanie/ścieżka lub niepoprawny format ścieżki | 400 |
| Poprawna składnia, ale folder nie przejdzie walidacji | Przyjęte zadanie przechodzi do `FAILED` |
| Nieznane, usunięte z historii albo utracone po restarcie ID | 404 |
| ID nie jest poprawnym UUID | 400 |

Po uruchomieniu Springa można sprawdzić w PowerShell:

```powershell
$scanJob = Invoke-RestMethod -Method Post `
  -Uri 'http://127.0.0.1:8080/api/scan/jobs' `
  -ContentType 'application/json' -Body '{"folderPath":"demo-images"}'
$scanJob | Format-List
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/scan/jobs/$($scanJob.id)" |
  ConvertTo-Json -Depth 4
```

Ostatnie polecenie można powtarzać. Mały folder może skończyć się przed pierwszym
GET-em — to nie błąd. HTTP 202 nie oznacza zakończenia ani poprawności folderu.
Powyższy POST naprawdę skanuje i zapisuje katalog; testy automatyczne zamiast
lokalnej bazy korzystają z osobnego PostgreSQL-a oraz plików tymczasowych.

## 8. Ograniczenia i następny krok

Opisy zadań są tylko w pamięci Javy, nie w PostgreSQL. Restart traci postęp
i historię, ale nie zapisany wcześniej katalog. Nie ma automatycznego wznowienia,
anulowania, procentu, SSE/WebSocket ani endpointu historii wszystkich zadań.
Takie rozszerzenia wymagają osobnego ustalenia. Nie dodajemy migracji.

React korzysta z nowego POST-a i odpytuje to samo ID około raz na sekundę,
bez nakładania żądań. Po COMPLETED odświeża katalog i pobiera próbkę odrzuceń;
przy FAILED pokazuje błąd bez udawania udanego zapisu. Cleanup przerywa żądania
UI i timer, nie skan w Javie. Po zerwaniu połączenia można ponowić odczyt tego
samego zadania. Restart strony traci śledzenie, nie anuluje skanu. Rust bez zmian.

Testy sprawdzają powrót przed workflow, postęp, niezmienność migawek, wykluczanie
równoczesnych startów, błędy, zwolnienie zasobu, limit historii, kontrakt HTTP
oraz rzeczywisty zapis. Test integracyjny zatrzymuje zapis kontrolowaną bramką:
widzi `SAVING` i brak rekordu, następnie zwalnia bramkę, czeka na zakończenie
i potwierdza `COMPLETED`, rekord w bazie oraz niezmienione bajty oryginału.

Weryfikacja 2026-10-07: `mvnw.cmd clean verify` zakończone BUILD SUCCESS.
93 testy: 91 zaliczonych, 2 dotychczasowe pominięcia (ręczny benchmark oraz
brak uprawnień do utworzenia linku symbolicznego w Windows).
Testy integracyjne korzystały z tymczasowego PostgreSQL-a 18.6 w Testcontainers;
nie uruchamiano skanu ani migracji na lokalnej bazie właściciela.
W logu pozostaje istniejące ostrzeżenie Mockito o dynamicznym dołączaniu agenta
w Javie 25 — nie jest niepowodzeniem testów. Frontend: 20 testów, build i lint
poprawne. Ręczny test przeglądarki używał rzeczywistego Springa i osobnego
tymczasowego PostgreSQL-a; Tauri nie uruchamiano w tym etapie.
