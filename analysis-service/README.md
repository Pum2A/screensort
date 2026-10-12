# ScreenSort Analysis Service

Status 2026-10-12: działający samodzielny OCR CPU i wspólne przygotowanie obrazu.
HTTP nadal udostępnia tylko health. Florence jest kandydatem zapisanym w kodzie,
bez zainstalowanego runtime, pobranego modelu i potwierdzonej inferencji.
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

W `vision/analyzer.py` znajduje się kandydat Florence-2-base-ft, natywna klasa
Transformers, przypięty revision, CPU float32 i `local_files_only=True`.
`torch` i `transformers` nie są jeszcze zależnościami projektu; model nie został
pobrany. Nie próbuj na tym etapie uruchamiać go jako gotowej funkcji.
Import koordynatora nie wymaga tych paczek: import typu wizji działa tylko
dla narzędzi sprawdzających typy. Testy koordynatora mają jawnie zastępcze silniki,
nie są testem jakości Florence. Następny krok wymaga uzgodnionej instalacji
runtime, przygotowania lokalnego cache i próby prawdziwej inferencji.

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
│   │   └── analyzer.py             jeden obraz → OCR + wizja → wspólny wynik
│   ├── ocr/
│   │   ├── __init__.py              oznaczenie podpakietu
│   │   └── recognizer.py            RapidOCR CPU
│   └── vision/
│       ├── __init__.py              oznaczenie podpakietu
│       └── analyzer.py              kandydat Florence; runtime nieuruchomiony
└── tests/
    ├── test_health.py               istniejący kontrakt HTTP
    ├── test_image_preparation.py    limity, format, RGB i zwalnianie zasobów
    ├── test_ocr_recognizer.py       delegowanie i zachowany interfejs OCR
    ├── test_image_analyzer.py       składanie wyników i błędy; zastępcze silniki
    └── test_ocr_integration.py      opcjonalny rzeczywisty OCR, syntetyczny obraz
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
4. Uruchomić kandydata Florence na jednym obrazie po uzgodnieniu zależności
   i lokalnego cache. Dopiero potem składać modele przez lifespan;
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
uruchomienia Florence, kontenerowego wdrożenia ani automatycznego startu z Tauri.
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
