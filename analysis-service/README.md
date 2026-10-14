# ScreenSort Analysis Service

Status 2026-10-12: działający samodzielny OCR, Florence i wspólny workflow CPU.
HTTP nadal udostępnia tylko health. Prawdziwy OCR i opis wizualny są sprawdzone
offline w konsoli oraz w testach integracyjnych na syntetycznym obrazie.
Nie ma jeszcze połączenia z Javą ani rzeczywistych wyników analizy w React.

To osobny lokalny proces Pythona w tym samym repozytorium Git. Ma własny pakiet,
zależności, testy i polecenie uruchomienia. Nie jest kolejnym katalogiem wewnątrz
Springa. Granice odpowiedzialności opisuje
[ADR-004](../docs/adr/ADR-004-local-python-analysis-service.md).

## Co działa teraz

`GET /health` zwraca HTTP 200 i:

```json
{
  "service": "screensort-analysis",
  "status": "ok",
  "analysis_available": false
}
```

To potwierdza wyłącznie, że proces odpowiada. Nie oznacza, że potrafi analizować
obrazy przez HTTP. `/ready` i `/api/v1/analyses` nie istnieją jeszcze. Nie zwracamy fikcyjnych
wyników. `/docs` pokazuje dokumentację bieżącego API; `/openapi.json` jego kontrakt.

## Analiza jednego obrazu w kodzie Pythona

- `image.preparation.prepare_image(bytes)` sprawdza limit 50 MiB, rzeczywisty
  PNG/JPEG, bok do 20 000 px i 40 mln pikseli przed pełnym dekodowaniem. Zwraca
  niezależny obraz RGB; przezroczystość składa na białym tle. Wywołujący musi
  zamknąć wynik przez `contextlib.closing` albo `image.close()`.
- `OcrRecognizer.recognize(bytes, OcrOptions())` zachowuje istniejący interfejs
  do konsoli. Przygotowuje obraz, deleguje rozpoznanie i zwalnia obraz.
- `OcrRecognizer.recognize_prepared(image, options)` używa już odczytanego RGB;
  nie zamyka obrazu należącego do wywołującego. `duration_ms` mierzy rozpoznanie,
  bez przygotowania, ale pierwszy przebieg może obejmować lazy loading silnika.
- `ImageAnalyzer(ocr, vision).analyze(bytes, options)` przygotowuje jeden obraz,
  oblicza SHA-256 oryginalnych bajtów, wywołuje kolejno OCR i wizję i składa
  `ImageAnalysisResult`. Oba silniki otrzymuje w konstruktorze, nie tworzy ich
  na nowo przy każdym obrazie. Błędy propaguje; częściowe wyniki dopiero
  ustalimy przed API analizy.
- `ImageAnalysisResult.visual` jest opcjonalne dla zgodności z przykładami
  OCR-only. Pełny koordynator wypełnia oba wyniki, jeśli oba silniki się powiodą.

Silnik OCR to RapidOCR 3.10.0 + ONNX Runtime CPU. Lista języków `pl`/`en`
ogranicza opcje wejściowe; nie przełącza modelu ani nie filtruje języka wyniku.
Silnik wielojęzyczny może zwrócić także napisy w innym języku.

`vision/analyzer.py` używa Florence-2-base-ft: natywna klasa Transformers,
przypięty revision, CPU float32 i `local_files_only=True`. Model i runtime
zweryfikowano lokalnie. Opis jest na razie krótki i po angielsku: `<CAPTION>`.
To propozycja opisu, nie gwarancja wykrycia wszystkich obiektów ani ich znaczenia.
Nie wykonujemy tłumaczenia, kategoryzacji ani zmiany nazw.

PyTorch/Transformers są opcjonalnym zestawem `vision`; zwykłe OCR ich nie wymaga.
Windows/Linux korzystają z oficjalnego indeksu CPU, bez bibliotek CUDA.
Import koordynatora bez wizji nadal działa. Testy jednostkowe koordynatora mają
jawnie zastępcze silniki; osobny test integracyjny uruchamia oba prawdziwe modele.
Nie traktuj tych testów jako pełnej oceny trafności opisów.

Dokładne metody i wyjaśnienia po polsku:
[Analiza obrazu krok po kroku](../docs/analiza-obrazu-krok-po-kroku.md).

## Uruchomienie w development

Wymagane: Python 3.12–3.14 i `uv`, narzędzie do instalacji zależności i uruchamiania
projektów Pythona. Ten etap sprawdzono na Pythonie 3.14.4 i uv 0.11.25; nie jest to
deklaracja zgodności przyszłych bibliotek ML z każdą z tych wersji Pythona.

Nie instaluj zależności globalnie. `uv sync` tworzy lokalne `.venv`.
Instalacja narzędzia: [oficjalna dokumentacja uv](https://docs.astral.sh/uv/getting-started/installation/).

Z głównego katalogu repozytorium, w PowerShell:

```powershell
cd analysis-service
uv sync --locked --no-python-downloads
uv run --locked --no-python-downloads python -m uvicorn screensort_analysis.main:create_app --factory --host 127.0.0.1 --port 8001 --workers 1
```

Pierwsza instalacja pobiera zależności z PyPI. `--locked` sprawdza zgodność
`pyproject.toml` z zatwierdzonym `uv.lock`, zamiast zmieniać wersje przy każdym
uruchomieniu. `--no-python-downloads` wymaga już zainstalowanego Pythona.
Pliku `.venv` nie commitujemy ani nie kopiujemy między komputerami.

W drugim terminalu:

```powershell
Invoke-RestMethod http://127.0.0.1:8001/health
```

Można też otworzyć `http://127.0.0.1:8001/docs` w przeglądarce. Ctrl+C zatrzymuje
wyłącznie ten serwis. Nie trzeba uruchamiać Springa, Reacta, Tauri ani Dockera,
żeby sprawdzić ten szkielet. Nie wystawiaj go przez `--host 0.0.0.0`.

## Florence i cały wynik w konsoli

Z `analysis-service`, jednorazowo na danym urządzeniu:

```powershell
uv sync --extra vision --locked --no-python-downloads
uv run --extra vision --locked --no-python-downloads python -m screensort_analysis.vision.download_model --model-cache models
```

Pierwsze polecenie instaluje dodatkowy runtime we własnym `.venv`, nie globalnie.
Drugie jawnie pobiera około 469 MB danych publicznego modelu do `models/`:
przypięty revision i wyłącznie wskazane pliki danych oraz safetensors, bez
zdalnych plików Python. Nie wymaga konta ani tokenu Hugging Face.
`models/`, `.venv/` i cache nie trafiają do Git; na laptopie trzeba je odtworzyć.
Ostrzeżenie o braku symlinków Windows oznacza zwykłe kopie w cache, nie błąd
modelu. Nie trzeba uruchamiać aplikacji jako administrator ani zmieniać Windows.

Potem wybierz swój istniejący obraz zamiast przykładowej ścieżki:

```powershell
uv run --extra vision --locked --offline --no-python-downloads python -m screensort_analysis.image.console --image "C:/Pictures/example.png" --model-cache models --cpu-threads 4 --runs 2
```

Konsola odczytuje tylko wskazany plik, do 50 MiB, bez zapisu. To lokalne narzędzie
developerskie, nie endpoint przyjmujący ścieżki od klienta. Nie skanuje folderów.
Model pracuje offline, z wyłączoną telemetrią i niejawnym tokenem Hub w tym
procesie. JSON zawiera runtime, osobny czas utworzenia silników i dwa wyniki:
hash oryginalnych bajtów, tekst OCR, opis wizualny, wersje oraz czasy.
Treść jest wypisywana na Twoją prośbę; nie zapisuj prywatnych wyników do Git.

Przy uruchamianiu wizji zawsze dodawaj `--extra vision`: zwykłe `uv run` może
zsynchronizować środowisko bez opcjonalnych paczek. CPU threads dotyczy tego
procesu konsoli; 4 to początkowy wybór, nie udowodnione optimum.

## Sprawdzanie

Z `analysis-service`:

```powershell
uv run --locked --no-python-downloads python -m pytest
uv run --locked --no-python-downloads python -m ruff check src tests
uv run --locked --no-python-downloads python -m ruff format --check src tests
uv build --no-python-downloads
```

TestClient sprawdza rzeczywisty routing FastAPI w pamięci, bez serwera TCP,
PostgreSQL i modeli. Osobna ręczna kontrola `/health` sprawdza uruchomienie
Uvicorna przez TCP. `uv build` tworzy paczkę Pythona, nie instalator aplikacji EXE.

Zwykły pytest sprawdza również przygotowanie obrazu, delegowanie OCR i składanie
wyników przy użyciu zastępczych silników. Nie ładuje modeli ani prywatnych zdjęć.
Jeden test prawdziwego OCR jest domyślnie pominięty. Aby go włączyć w PowerShell:

```powershell
$env:SCREENSORT_RUN_OCR_TESTS = "1"
uv run --locked --no-python-downloads python -m pytest tests/test_ocr_integration.py -v
Remove-Item Env:SCREENSORT_RUN_OCR_TESTS
```

Ten test tworzy obraz z tekstem w pamięci i blokuje pobieranie przez `requests`.
Wymaga dostępnych lokalnie modeli RapidOCR; nie pobiera modelu Florence.

Pełny test OCR + Florence, po przygotowaniu modelu:

```powershell
$env:SCREENSORT_RUN_OCR_TESTS = "1"
$env:SCREENSORT_RUN_VISION_TESTS = "1"
uv run --extra vision --locked --offline --no-python-downloads python -m pytest
Remove-Item Env:SCREENSORT_RUN_OCR_TESTS
Remove-Item Env:SCREENSORT_RUN_VISION_TESTS
```

Test wizji blokuje sockety i nie pobiera modelu. Sprawdza pełne wywołanie,
niepusty opis, rzeczywisty CPU, powiązanie hasha i JSON. Nie zakłada, że dokładne
słowa opisu są prawidłowym opisem każdego obrazu. Obie integracje są domyślnie
pominięte, więc zwykłe testy nie potrzebują lokalnego modelu Florence.

## Gdzie jest który kod

```text
analysis-service/
├── pyproject.toml                  nazwa pakietu, zależności, konfiguracja narzędzi
├── uv.lock                         dokładne wersje i hashe zależności
├── src/screensort_analysis/
│   ├── __init__.py                  oznaczenie pakietu Pythona
│   ├── main.py                     składanie aplikacji HTTP
│   ├── api/
│   │   ├── __init__.py              oznaczenie podpakietu
│   │   ├── health.py                obsługa GET /health
│   │   └── schemas.py               opcje oraz wyniki OCR/wizji
│   ├── image/
│   │   ├── __init__.py              oznaczenie podpakietu
│   │   ├── preparation.py          limity, odczyt PNG/JPEG i przygotowanie RGB
│   │   ├── analyzer.py             jeden obraz → OCR + wizja → wspólny wynik
│   │   └── console.py              lokalna próba jednego pliku i pomiary JSON
│   ├── ocr/
│   │   ├── __init__.py              oznaczenie podpakietu
│   │   └── recognizer.py            RapidOCR CPU
│   └── vision/
│       ├── __init__.py              oznaczenie podpakietu
│       ├── analyzer.py              rzeczywisty Florence CPU, lokalny model
│       ├── model.py                 wspólna tożsamość modelu i lista danych
│       └── download_model.py        jawne przygotowanie cache przed inferencją
└── tests/
    ├── test_health.py               istniejący kontrakt HTTP
    ├── test_image_preparation.py    limity, format, RGB i zwalnianie zasobów
    ├── test_ocr_recognizer.py       delegowanie i zachowany interfejs OCR
    ├── test_image_analyzer.py       składanie wyników i błędy; zastępcze silniki
    ├── test_ocr_integration.py      opcjonalny rzeczywisty OCR, syntetyczny obraz
    ├── test_vision_integration.py   opcjonalny prawdziwy OCR + Florence offline
    ├── test_vision_download.py      przypięty kontrakt pobierania, bez sieci
    └── test_image_console.py        ograniczony odczyt testowych plików
```

Nazwa dystrybucji to `screensort-analysis`, a importu `screensort_analysis`:
w nazwie importu Pythona nie używa się myślnika. `src` oddziela instalowany kod
od dokumentacji i testów. Testy importują zainstalowany pakiet, bez ręcznego
dodawania katalogów do `sys.path`.

`create_app` jest funkcją składającą aplikację, podobnie jak punkt uruchomienia
Springa; nie powinna zawierać całej analizy. `APIRouter` grupuje endpointy, podobnie
jak kontroler. `HealthResponse` jest modelem Pydantic opisującym JSON, podobnie
jak DTO lub typ odpowiedzi we frontendzie. `no-store` każe nie zapisywać odpowiedzi
health w cache HTTP, aby przyszła kontrola procesu nie pokazywała starego wyniku.

## Kolejne małe kroki — do wspólnego napisania

1. Ustalić kontrakt jednej analizy: ograniczone bajty obrazu i opcje → częściowe
   wyniki OCR/wizualne, SHA-256 wejścia, wersje modeli, tryb CPU/GPU i błędy.
2. Dodać kontrolę dostępu dla Javy, limity wejścia, bezpieczny odczyt obrazu
   i ograniczenie współbieżności. Loopback sam w sobie nie jest autoryzacją.
3. OCR już działa samodzielnie; rozszerzyć ocenę jakości o opisane przykłady
   polskich i angielskich zrzutów, zanim uznamy go za gotowy dla całego katalogu.
4. Florence jest uruchomiony; rozszerzyć próbę o opisany zbiór zrzutów i
   porównać trafność krótkiego oraz szczegółowego opisu. Składać modele przez lifespan;
   `/ready` powinien odzwierciedlać rzeczywiście dostępne możliwości.
5. Podłączyć klienta w Javie oraz osobne zadanie analizy; potem prawdziwy wynik
   w React. Nie przerabiać `ScanJob` na zadanie analizy.
6. Dodać opcjonalny backend NVIDIA/CUDA z CPU jako obsługiwanym wariantem.
   Zgodność sterownika, bibliotek i modelu musi być sprawdzona w praktyce.

Nie tworzymy pustych `domain`, `infrastructure`, `utils` ani `shared` na zapas.
Obecne `image`, `ocr` i `vision` wynikają z funkcji, która rzeczywiście powstaje.
Python nie dostanie repozytoriów JPA ani dostępu do bazy katalogu. Nie przyjmuje
dowolnych ścieżek, nie skanuje dysku i nie zmienia oryginalnych plików.

Nie dodano jeszcze autoryzacji, limitu uploadu HTTP, kolejki, analizy przez API,
kontenerowego wdrożenia ani automatycznego startu z Tauri.
Limity dekodera nie ograniczają całej pamięci procesu: raw bytes, RGB, bufory
pomocnicze i modele mają własny koszt. Limit 40 mln pikseli jest początkową
polityką, nie wynikiem pomiaru maksymalnego bezpiecznego RAM dla każdej maszyny.
Publiczne wdrożenie jest poza zakresem. Dodanie procesu nie przyspiesza
automatycznie skanowania.

## Źródła decyzji

- [FastAPI — większe aplikacje i APIRouter](https://fastapi.tiangolo.com/tutorial/bigger-applications/).
- [FastAPI — testy](https://fastapi.tiangolo.com/tutorial/testing/).
- [uv — projekty i zależności](https://docs.astral.sh/uv/guides/projects/).
- [uv — integracja z FastAPI](https://docs.astral.sh/uv/guides/integration/fastapi/).
- [uv — opcjonalne warianty PyTorch CPU](https://docs.astral.sh/uv/guides/integration/pytorch).
- [Transformers — Florence-2](https://huggingface.co/docs/transformers/model_doc/florence2).
- [Przypięte pliki modelu](https://huggingface.co/florence-community/Florence-2-base-ft/tree/0b03b6f15a4a211370fb204aee4e7dd48887ea37).
