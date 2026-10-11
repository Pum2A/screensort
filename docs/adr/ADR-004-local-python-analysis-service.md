# ADR-004: Lokalny serwis analizy w Pythonie

Data: 2026-10-11

Status: Accepted — granica serwisu i szkielet; silniki OCR/wizualne nadal niewybrane

Zmienia wcześniejszy plan odkładania osobnego procesu analizy do czasu po MVP
w `architecture.md`, `roadmap.md` i `decyzje-techniczne.md`.
Nie zmienia zasad bezpieczeństwa ADR-001 ani wyboru Tauri z ADR-003.

## Kontekst

Właściciel chce rozwijać niezależny lokalny serwis OCR i analizy wizualnej,
z zachowaniem głównej logiki aplikacji w Javie. Python ułatwia eksperymenty
z bibliotekami modeli. Benchmark tego samego silnika ONNX w trzech językach
nie dowiódł istotnej przewagi Pythona jako języka wykonywania inferencji:
[wyniki i ograniczenia](../benchmark-analiza-jezyki.md).

Motywacja: niezależne zależności modeli, możliwość wymiany silników i izolacja
awarii procesu, nie deklarowane przyspieszenie przez samą zmianę języka.

## Decyzja

W jednym kanonicznym repozytorium utrzymujemy Java core, React/Tauri oraz
`analysis-service` — osobno uruchamiany pakiet `screensort_analysis` w Pythonie.
Nie tworzymy zagnieżdżonego `.git` i nie rozbijamy skanowania na mikroserwisy.

To lokalny serwis obliczeniowy z własnym API i cyklem życia. Nie nazywamy całego
produktu gotowym systemem rozproszonych mikroserwisów. Sam katalog nie stanowi
granicy serwisu: tę granicę tworzą osobny proces, kontrakt HTTP i brak zależności
od wewnętrznego kodu oraz tabel drugiego procesu.

| Właściciel | Odpowiedzialność |
|---|---|
| Java | Katalog, bezpieczeństwo źródła, zadania, limity zlecania, timeouty, zapis wyników, propozycje, decyzje użytkownika i przyszłe zweryfikowane kopiowanie. |
| Python | Ograniczone wejście obrazu, OCR i analiza wizualna, wersje modeli, rzeczywisty tryb obliczeń, częściowe wyniki i błędy. |
| React | Wywołuje Javę, pokazuje stan i wyniki; nie komunikuje się bezpośrednio z silnikiem Python. |
| Tauri/Rust | Okno i picker; automatyczny nadzór procesów dopiero w etapie pakowania. |

Docelowy przepływ, jeszcze niepołączony:

```text
React → Java: analizuj wskazany rekord katalogu
        Java: sprawdź źródło, utwórz osobne zadanie analizy
        Java → Python: bajty obrazu i uzgodnione opcje
               Python: OCR + analiza wizualna
        Java ← Python: częściowe wyniki + hash wejścia + wersje + błędy
        Java: zapisz wynik przypisany do właściwej zawartości
React ← Java: stan zadania i wyniki do przeglądu
```

Kontrakt analizy otrzyma wersję, np. `/api/v1/analyses`. Nie obejmie encji JPA,
ścieżek systemowych ani poleceń SQL. Health jest niewersjonowanym endpointem
technicznym `/health`, niezależnym od przyszłego formatu wyniku analizy.
Zmiana kontraktu nie może cicho zepsuć klienta Javy; wymaga testów zgodności.

Python początkowo nie przechowuje trwałego stanu i nie posiada bazy danych.
Nie współdzieli schematu PostgreSQL ani kodu repozytoriów. Własna baza serwisu
nie jest wymagana, jeśli nie ma danych, za które samodzielnie odpowiada.

## Struktura i zależności

- `main.py`: tworzy aplikację i łączy komponenty.
- `api/`: transport HTTP i schematy danych.
- `ocr/` oraz `vision/`: powstaną przy implementacji odpowiednich silników;
  ich kod nie będzie zależał od kontrolera HTTP.
- `tests/`: testy istniejących zachowań; bez rzeczywistych prywatnych obrazów.
- `pyproject.toml` + `uv.lock`: definicja pakietu i powtarzalne wersje zależności.

Nie tworzymy pustych warstw ani ogólnego folderu `utils`. Gdy pojawi się realna
potrzeba wymiany silnika, dodamy wąski interfejs odpowiadający jego wejściu/wyjściu,
a nie rozbudowaną hierarchię przed pierwszą działającą analizą.

## Bezpieczeństwo i wykonanie — warunki przed analizą

- Serwer tylko na loopback; token komunikacji z Javą przed endpointem analizy.
- Java nie przekazuje użytkownikowi możliwości otwierania dowolnej ścieżki
  przez Python. Dane obrazu muszą zostać ponownie ograniczone i sprawdzone
  przez proces analizy; to oddzielna granica bezpieczeństwa.
- Limit bajtów, wymiarów/pikseli, czasu i pracy w toku; brak nieograniczonej
  kolejki. Timeout HTTP nie gwarantuje zatrzymania natywnej inferencji.
- Bez logowania treści OCR i bajtów prywatnych obrazów.
- Wynik związany z hashem faktycznie przeanalizowanej zawartości oraz wersją
  silnika/modelu. Rozmiar i data modyfikacji nie są dowodem zgodności bajtów.
- Modele ładowane raz podczas startu. Początkowo jeden proces serwera;
  liczba procesów/wątków wynika z pomiarów i kosztu RAM/VRAM.
- CPU jako działający wariant; NVIDIA/CUDA później, po wyborze i weryfikacji
  modeli. Bez obietnicy przyspieszenia każdego modelu i każdego GPU.

## Alternatywy i koszty

Java z ONNX Runtime pozostaje poprawną alternatywą, o mniejszej liczbie procesów
i narzędzi. Wybieramy Python ze względu na pracę z modelami, akceptując koszt
nowego runtime, komunikacji, obsługi awarii i późniejszego instalatora.

Rust pozostaje warstwą natywną; nie przenosimy do niego analizy bez pomiarów.
Kafka, broker, Kubernetes i kilka niezależnych serwisów OCR/wizji nie rozwiązują
obecnego problemu jednego lokalnego użytkownika i nie są częścią tej decyzji.
Docker może być dodany jako sposób uruchomienia dla developera, ale nie jest
warunkiem korzystania z docelowej aplikacji desktopowej.

## Stan wdrożenia i następny krok

Istnieje tylko pakiet, uruchomienie HTTP, `/health`, schemat odpowiedzi i testy.
Health odpowiada `analysis_available: false`. Nie istnieją jeszcze klient Javy,
endpoint analizy, autoryzacja, readiness modeli ani rzeczywisty wynik w React.

Następny etap do wspólnego napisania: kontrakt pojedynczego obrazu i pierwszy
rzeczywisty wynik OCR, potem analiza wizualna. Zmiany backendu i modeli wymagają
omówienia z właścicielem, który chce sam pisać istotny kod.
