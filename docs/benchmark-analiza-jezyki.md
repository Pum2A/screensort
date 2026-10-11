# Analiza obrazu: pomiar Python / Java / Rust

Data: 2026-10-11. To lokalny eksperyment przed wyborem technologii, nie gotowa
funkcja analizy i nie ostateczna decyzja o mikroserwisie.

## Wniosek dla ScreenSort

Python jest rozsądnym kandydatem do osobnego procesu OCR i analizy wizualnej,
ponieważ ułatwia eksperymentowanie z modelami. **Ten pomiar nie dowodzi, że
Python jest najszybszym językiem ani że daje najlepsze opisy obrazów.**

Przy jednym wątku trzy warianty mają bardzo podobny czas uruchamiania tego
samego modelu. W tym zadaniu konfiguracja silnika miała większy wpływ niż język.
Nie ma podstaw do przepisywania skanera Javy na Rust/Python dla samej szybkości.

## Co faktycznie zmierzono

- Windows 11, Intel Core i5-10400F, 6 rdzeni / 12 wątków logicznych, 23,92 GiB RAM.
- Wykryta karta: AMD Radeon RX 6600. W benchmarku **nie używano GPU**.
- Java: Temurin 25.0.4.1; Python: 3.14.4; Rust: 1.95.0, build release.
- ONNX Runtime 1.26.0: wszystkie języki ładowały identyczną DLL, sprawdzoną SHA-256.
- Java: oficjalne API ONNX; Rust: nieoficjalny wrapper `ort` 2.0.0-rc.13.
- Ten sam MobileNet v2 FP32, batch 1, wejście 1x3x224x224.
- 16 syntetycznych wejść RGB, ustalone ziarno, identyczne bajty we wszystkich procesach.
- CPUExecutionProvider, wykonanie sekwencyjne grafu, wszystkie optymalizacje;
  1 albo 4 wątki wewnątrz operacji, 1 wątek między operacjami.
- 5 osobnych procesów na konfigurację; 20 rozgrzewek i 200 mierzonych wywołań
  na proces. Łącznie 30 procesów i **6000 mierzonych wywołań**.
- Zmieniana kolejność uruchamiania języków, żadnych równoległych benchmarków.

„Uruchamianie modelu” to inference: podajemy modelowi liczby opisujące piksele,
otrzymujemy jego wynik. Nie dekodujemy ponownie PNG przy każdym wywołaniu.
MobileNet wybiera spośród 1000 klas ImageNet; nie wyciąga tekstu i nie pisze
opisów screenshotów. [Źródło modelu](https://github.com/onnx/models/tree/main/validated/vision/classification/mobilenet).

## Wyniki

W tabelach pokazano medianę pięciu median procesów. Mediana oznacza środkowy
wynik, mniej wrażliwy na pojedyncze opóźnienia niż średnia.

| Wariant | Czas / wejście: 1 wątek | Czas / wejście: 4 wątki | Zakres median procesów przy 4 wątkach |
|---|---:|---:|---:|
| Python | 8,189 ms | 3,064 ms | 2,824–3,489 ms |
| Java | 8,317 ms | 3,259 ms | 2,999–4,464 ms |
| Rust | 8,141 ms | 3,438 ms | 2,998–4,258 ms |

Przy jednym wątku rozstęp pomiędzy trzema zbiorczymi wynikami wynosi około 2,2%.
Przy czterech wątkach występuje większa zmienność i zakresy się pokrywają.
Nie nazywamy Pythona zwycięzcą wszystkich zastosowań na podstawie tej serii.

Zmiana 1 → 4 wątki obniżyła medianę w tym teście około **2,4–2,7 raza**.
To wątki **silnika modelu**, nie cztery workery naszego skanera.
Nie wolno mnożyć bez kontroli liczby workerów przez liczbę wątków modelu;
może to przeciążyć CPU. [Zarządzanie wątkami ONNX](https://onnxruntime.ai/docs/performance/tune-performance/threading.html).

P95 (mediana P95 poszczególnych procesów):

| Wariant | P95: 1 wątek | P95: 4 wątki |
|---|---:|---:|
| Python | 8,553 ms | 4,391 ms |
| Java | 8,680 ms | 4,530 ms |
| Rust | 8,500 ms | 4,400 ms |

P95 oznacza, że około 95% wywołań danego procesu mieściło się w tym czasie.
To nie maksymalny czas i nie P95 całej aplikacji.

Start i pamięć, konfiguracja 1-wątkowa:

| Wariant | Od startu procesu do pierwszego wyniku | Peak working set procesu |
|---|---:|---:|
| Python | 597 ms | 86,8 MiB |
| Java | 295 ms | 121,3 MiB |
| Rust | 130 ms | 71,7 MiB |

Start obejmuje zależności, inicjalizację silnika, model, wejścia i pierwszy wynik.
To nie start całego ScreenSort i nie pomiar Springa/FastAPI/Tauri. Pliki mogły
być w cache systemowym. Pamięć obejmuje cały proces testowy, jego bufory i DLL;
nie jest uniwersalnym kosztem danego języka ani zużyciem VRAM.
Nie zabijano ani nie izolowano innych programów użytkownika; możliwe zakłócenia
obciążeniem systemu. Wynik należy powtórzyć na laptopie i docelowym modelu.

Wszystkie procesy zwróciły zgodne 1000 wartości ostatniego wyniku; maksymalna
różnica względem referencji wyniosła **0**. Sam zgodny wynik nie ocenia jakości
rozpoznawania. Surowe czasy i wersje są w
[`results-2026-10-11.json`](../benchmarks/vision-bindings/results-2026-10-11.json).

## Czego nie można z tego wywnioskować

Nie zmierzono OCR, rozpoznawania naszych kategorii, opisywania scen, GPU,
dekodowania dużych zdjęć, HTTP, bazy, batchingu ani analizy 10 000 różnych plików.
Nie wolno obiecać „10 000 zdjęć w 30 sekund” na podstawie tego testu.
Poważniejszy model może mieć zupełnie inne koszty i wymagania.

Nie porównujemy pętli napisanej w czystym Pythonie z kodem Javy/Rusta. Wszystkie
trzy delegują obliczenia modelu do tego samego natywnego silnika. Python jest tu
sterowaniem, nie interpretuje wiersz po wierszu wszystkich obliczeń sieci.

## Jak uwzględnić przyszłe rozumienie obrazu

Potrzebujemy trzech różnych rzeczy, nie jednego „AI”:

| Zadanie | Co otrzymujemy | Kandydat do kolejnego eksperymentu, nie wybrana implementacja |
|---|---|---|
| OCR | Tekst widoczny na obrazie, PL/EN | Porównanie silników OCR na małym zbiorze z poprawnym tekstem. |
| Kategoria / tagi | Dopasowanie obrazu do np. „kod”, „dokument”, „produkt” | CLIP lub podobny model obrazu i tekstu. |
| Opis sceny | Zdanie np. „osoba stojąca obok samochodu” | Model generujący opis obrazu, np. rodzina BLIP albo inny lokalny VLM. |

CLIP porównuje obraz z podanymi opisami. Nie jest sam w sobie generatorem
swobodnych zdań; wynik podobieństwa nie jest automatycznie skalibrowanym
prawdopodobieństwem poprawności. [Dokumentacja CLIP](https://huggingface.co/docs/transformers/main/model_doc/clip).
BLIP ma zastosowania obejmujące generowanie opisów obrazu.
[Dokumentacja BLIP](https://huggingface.co/docs/transformers/main/model_doc/blip).
Nazwy są kandydatami do prób, nie obietnicą jakości na screenshotach.

Moja rekomendacja: pozostawić Java jako core, a **Python jako rozważany, osobny
lokalny proces analizy**. Wybrać go dla łatwości pracy z modelami, nie jako
udowodniony „najszybszy język”. Rust pozostaje dobrym narzędziem do natywnej
integracji, ale ten pomiar nie uzasadnia pisania w nim analizy zamiast Pythona.
Java jest również realną alternatywą, jeżeli wybrane modele i ich preprocessing
da się wygodnie obsłużyć przez ONNX.

Model ładujemy raz przy starcie procesu, a potem używamy wielokrotnie.
Nie uruchamiamy Pythona i nie wczytujemy wag od nowa dla każdego zdjęcia.
Java nadal odpowiada za bezpieczeństwo źródeł, zadania, katalog i zapis wyników.
Proces analizy nie ma dowolnie zmieniać plików ani bezpośrednio zapisywać katalogu.

## AMD i przyszły GPU

Na tym komputerze nie ma karty NVIDIA. CUDA nie jest rozwiązaniem dla wykrytej
RX 6600. Dla Windows należy sprawdzić zgodny backend i model, a nie tylko
zainstalować `torch` i uznać, że GPU zaczęło pracować.

ONNX DirectML obsługuje zgodny sprzęt DirectX 12 różnych producentów. Dokumentacja
zaznacza obecnie, że DirectML pozostaje wspierany, ale nowe funkcje Windows są
rozwijane w WinML. Oba wymagają weryfikacji konkretnej integracji i modelu.
Nie wykonano benchmarku tych backendów ani pomiaru VRAM.
[DirectML](https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html),
[ONNX na Windows](https://onnxruntime.ai/docs/get-started/with-windows.html).

## Najbliższy krok

Przed rozbudową mikroserwisu przygotować 20–30 reprezentatywnych, świadomie
wybranych obrazów: kod, komunikat błędu, dokument PL/EN, produkt, rozmowa,
meme i zdjęcie sceny/osoby. Oryginały pozostają tylko do odczytu, analiza lokalna.
Każdy obraz powinien mieć oczekiwaną kategorię, tekst oraz krótkie fakty wizualne.

W Pythonie porównać kandydatów: poprawność tekstu, trafność kategorii/opisu,
czas po rozgrzaniu, start, RAM/VRAM, rozmiar instalacji, zgodność z AMD i licencję.
Wybrać możliwie lekki model, który daje użyteczne wyniki, zanim dodamy cięższy VLM.
Dopiero później ustalić kontrakt Java ↔ analiza i podłączyć rzeczywiste wyniki
do istniejącego UI. Nie wdrożono tego kroku za właściciela.

## Co zmieniło się w repozytorium

Dodano wyłącznie narzędzia eksperymentu w `benchmarks/vision-bindings` i ten raport.
Nie zmieniono kodu produkcyjnego, zależności głównego POM, Cargo Tauri, bazy
ani źródłowych zdjęć. Nie utworzono FastAPI ani serwisu analizy.
Instrukcja powtórzenia: [`README benchmarku`](../benchmarks/vision-bindings/README.md).
