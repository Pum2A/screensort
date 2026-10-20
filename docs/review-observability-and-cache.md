# Metryki i opcjonalny Redis — zakres do code review

Stan: 2026-10-20. To etap diagnostyki i cache podglądów, **nie nowy model
rozpoznawania obrazów**. Źródłowe zdjęcia i decyzje w PostgreSQL pozostają bez zmian.

## Co rzeczywiście doszło

`ProductMetrics` mierzy czas walidacji folderu, odczytu znanych plików, skanu,
zapisu katalogu, odczytu źródła analizy, żądania do Pythona i zapisu analizy.
Zlicza wyniki skanu i trafienia/pominięcia/błędy cache. Z odpowiedzi Pythona
odczytuje osobno czas OCR i wizji. Nie mierzy jeszcze rozruchu Rust/Python,
pamięci procesu Python ani czasu oczekiwania na blokadę `AnalysisProcessor`.

Prometheus jest osobnym narzędziem developerskim. Eksport działa wyłącznie po
włączeniu profilu `observability`, na `127.0.0.1:19091`. W profilu `desktop`
nawet dodatkowe podanie `observability` nie włącza tego portu. Nie odsłaniamy
`env`, `configprops`, `heapdump`, `logfile` ani innych endpointów diagnostycznych.

Redis jest opcjonalnym drugim poziomem cache zakodowanych podglądów PNG.
Nie jest cache inferencji, nie przechowuje surowego OCR ani encji JPA.
Profil `redis-cache` tworzy klienta tylko poza `desktop`. Bez tego profilu nie
powstaje klient ani połączenie Redis. Nie dokładamy serwera Redis do instalatora.

## Przepływ podglądu

```text
Żądanie podglądu po ID katalogu
  → odczyt opisu pliku z PostgreSQL
  → sprawdzenie folderu, ścieżki, linków i aktualnych metadanych
  → klucz SHA-256 opisu źródła + wariantu podglądu
  → L1: pamięć procesu Java
  → przy braku: L2 Redis, tylko jeśli jawnie włączony
  → przy braku lub awarii: dotychczasowy ograniczony dekoder
  → sprawdzenie, czy źródło nie zmieniło się podczas odczytu
  → zapis PNG do L1 i opcjonalnego L2
```

Również trafienie w cache wymaga kontroli źródła. Usunięty plik albo folder
zastępujący obraz nie dostanie starej miniatury. Dopisaliśmy kontrolę również po
odczycie z cache, ponieważ Redis może wymagać oczekiwania na połączenie.
Rozmiar/data/file key to nadal kontrola metadanych, nie dowód niezmienności
bajtów w obliczu świadomego podrobienia metadanych.

L1: dotychczasowe LRU, maksymalnie 128 wpisów / 16 MiB. L2: TTL 10 minut,
maksymalnie 2 MiB na zapisywany podgląd. Większy PNG nadal może działać przez
L1/dekoder. Kontener Redis ma budżet danych 64 MiB i limit pamięci procesu 128 MiB.
Nie zapisuje RDB ani AOF; po zatrzymaniu traci cache. Nie traci się katalogu,
bo katalog jest w PostgreSQL, nie w Redisie.

Miniatura może zawierać dane prywatne. SHA w kluczu nie anonimizuje obrazu.
Cache jest lokalny, developerski i opt-in; nie wystawiaj portu na LAN. Brak
zapisu Redis na dysk nie oznacza ochrony przed administratorami, złośliwym
lokalnym procesem, zrzutem pamięci czy systemowym plikiem wymiany.

## Najważniejsze fragmenty, po ludzku

W `ScanWorkflow`:

```java
AllowedRoot allowedRoot = metrics.measure(FOLDER_VALIDATION, () -> folderValidator.validate(folder));
```

`FOLDER_VALIDATION` to stała etykieta operacji, a nie nazwa folderu.
`() -> ...` przekazuje czynność do wykonania. Nie uruchamia dodatkowego wątku.
`measure` uruchamia zegar, wykonuje dokładnie tę czynność i oddaje jej oryginalny
wynik. Dlatego po lewej nadal mamy `AllowedRoot`. Jeśli walidator rzuci wyjątek,
ten sam wyjątek idzie dalej; nie zaczynamy kolejnych etapów.

W `ProductMetrics.measure`:

```java
var sample = Timer.start(registry);
boolean success = false;
try {
  T value = action.run();
  success = true;
  return value;
} finally {
  sample.stop(registry.timer("screensort.operation", "operation", label(operation),
      "outcome", success ? "success" : "failure"));
}
```

`registry` przechowuje liczniki i pomiary, nie obrazy. `sample` pamięta początek
pomiaru. `T` oznacza typ wyniku konkretnej czynności: tu może być `AllowedRoot`,
w innym miejscu `ScanResult`. `action.run()` wykonuje przekazaną czynność.
`success` zmienia się dopiero po jej poprawnym powrocie. `finally` wykona się
zarówno przy `return`, jak i przy wyjątku, więc błędy również mają czas wykonania.
Nie ma tutaj `catch` zamieniającego awarię w sukces.

`operation` i `outcome` to etykiety metryki. Ich wartości pochodzą z małego
zestawu stałych. Nie tworzymy osobnej serii dla każdego z 10 000 plików i nie
zapisujemy w metrykach ścieżek, OCR, hashy, numerów dokumentów ani tokenów.

W `PreviewStore` odczyt `memory.get(key)` jest pierwszy. Dopiero brak wyniku
uruchamia odczyt Redis. Trafienie Redis jest zapisywane także do L1, więc następny
odczyt nie potrzebuje sieci. Klucz jest skrótem ID, ścieżki, wariantu, rozmiaru,
daty, file key i znanego SHA źródła. Prefiks `v1` pozwala w przyszłości zmienić
algorytm generowania miniatur bez odczytu niezgodnego starego cache.

W `RedisPreviewCache`:

```java
redis.opsForValue().set(key, bytes, TTL);
```

To zapis pary klucz → zakodowany PNG z czasem wygaśnięcia. Nie jest to zapis
oryginalnego pliku ani wywołanie modelu. Serializacja to tekstowe klucze i surowe
bajty, nie deserializacja obiektów Java. Timeout operacji/połączenia to 100 ms.
Pierwsza awaria może więc dołożyć oczekiwanie; po awarii przez 30 sekund klient
jest omijany. Awaria nie przerywa podglądu. Te wartości wymagają pomiarów, nie
są obietnicą, że Redis przyspieszy każdy przypadek.

`@Profile("redis-cache & !desktop")` oznacza dwa warunki naraz: profil cache jest
włączony i nie pracujemy w instalatorze desktopowym. `@Bean` przekazuje Springowi
gotowy obiekt narzędzia. Konstruktor `PreviewStore` dostaje `Optional`, bo Redis
może celowo nie istnieć. Główne działanie aplikacji nie zależy od niego.

## Jak uruchomić narzędzia

Docker Desktop musi działać. W katalogu repozytorium:

```powershell
docker compose -f compose.observability.yaml up -d
```

Uruchom backend z profilami `local,observability,redis-cache`, np. argumentem IDE:

```text
--spring.profiles.active=local,observability,redis-cache
```

Alternatywnie Maven Wrapper:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,observability,redis-cache"
```

Ten start używa developerskiego PostgreSQL z dotychczasowego `compose.yaml`.
Nie jest komendą do uruchamiania prywatnej bazy zainstalowanego desktopu.

Metryki: <http://127.0.0.1:19091/actuator/prometheus>.
Prometheus: <http://127.0.0.1:19090>. W jego Targets sprawdź, czy `screensort-dev`
ma stan UP. Konfiguracja `host.docker.internal` jest dla Docker Desktop.
Jeśli proxy Dockera nie dosięga loopback hosta, nie zmieniaj nasłuchu Javy na
`0.0.0.0`: użyj lokalnego Prometheusa lub osobno zaprojektowanego zabezpieczonego
połączenia. To zależność środowiska, nie powód do odsłonięcia diagnostyki w LAN.

Przykładowe zapytania PromQL:

```promql
sum by (operation, outcome) (rate(screensort_operation_seconds_count[5m]))

sum by (operation) (rate(screensort_operation_seconds_sum[5m]))
/
sum by (operation) (rate(screensort_operation_seconds_count[5m]))

histogram_quantile(0.95,
  sum by (le, operation) (rate(screensort_operation_seconds_bucket[5m])))

sum by (layer, outcome) (increase(screensort_preview_cache_total[5m]))

jvm_memory_used_bytes{area="heap"}
```

Pierwsze trzy dotyczą częstotliwości i czasu operacji, czwarte cache, ostatnie
pamięci JVM. Brak próbek / brak operacji może dać pusty wynik albo NaN.
Pamięć JVM to nie pamięć Pythona. Czas `analysis_request` obejmuje transport
i oczekiwanie na odpowiedź, natomiast `analysis_inference` pochodzi z zegara
wewnątrz konkretnego kroku OCR/wizji.

W skrypcie pakowania dodano moduł `jdk.management` do nowego runtime Java.
Ma udostępnić klasy wykorzystywane przez metryki GC. Nie przebudowuje to
automatycznie już zainstalowanej aplikacji ani istniejącego runtime w katalogu.

Zatrzymanie narzędzi, bez usuwania bazy aplikacji:

```powershell
docker compose -f compose.observability.yaml stop
```

## Co sprawdzają testy

- osobne pomiary sukcesu i wyjątku, z zachowaniem oryginalnego wyjątku;
- rzeczywisty format eksportu Prometheus i brak identyfikatorów źródła;
- rzeczywisty eksport HTTP na losowym porcie i odpowiedzi 404 dla env/heapdump/configprops/logfile;
- domyślnie zamknięty eksport, również przy `desktop,observability`;
- brak klienta Redis domyślnie i przy `desktop,redis-cache`;
- L1 bez Redis, trafienie L2 i brak ponownego odczytu L2 po napełnieniu L1;
- TTL, limit wartości, awaria odczytu/zapisu i powrót po okresie pomijania;
- dotychczasowe kontrole ścieżek, rozmiarów i źródłowych zdjęć;
- izolowany prawdziwy Redis w Testcontainers, jeśli Docker jest dostępny.

Pominięty test kontenerowy nie jest dowodem poprawnego połączenia z Redisem.
Tak samo poprawny format eksportu nie dowodzi, że kontener Prometheusa dosięga
portu hosta. To trzeba osobno zweryfikować w uruchomionym środowisku.

Weryfikacja lokalna 2026-10-20: zestaw bez testów bazy/kontenerów — 154 testy,
0 błędów, 2 wcześniejsze pominięcia środowiskowe; dodatkowy test rzeczywistego
eksportu HTTP — 1/1. Nowy test Redis w Testcontainers pominięty, bo Docker Desktop
nie działał. Konfiguracja Compose i składnia skryptu PowerShell przeszły kontrolę.
Nie wykonano benchmarku przyspieszenia ani nowego instalatora. Testy sieciowe
wymagały dostępu do loopback poza sandboxem. Lokalne testy Mockito uruchomiono
z jawnym agentem, ponieważ samodołączanie agenta było blokowane przez sandbox.

## Granica tego etapu

Nie dodano nowych modeli, treningu, fusion OCR/wizji, wykrywania dokumentów
wrażliwych, nowej galerii ani naprawy wznawiania procesu Python. Konkretny plan
następnych etapów: [plan-contextual-analysis.md](plan-contextual-analysis.md).

Dokumentacja użyta przy implementacji:
[Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html),
[Spring Data Redis](https://docs.spring.io/spring-data/redis/reference/redis/template.html),
[Prometheus](https://prometheus.io/docs/introduction/overview/).
