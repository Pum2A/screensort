# ScreenSort Analysis Service

Status: działający szkielet HTTP, bez OCR, modeli, GPU i połączenia z Javą.

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
obrazy. `/ready` i `/api/v1/analyses` nie istnieją jeszcze. Nie zwracamy fikcyjnych
wyników. `/docs` pokazuje dokumentację bieżącego API; `/openapi.json` jego kontrakt.

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

## Gdzie jest który kod

```text
analysis-service/
├── pyproject.toml                  nazwa pakietu, zależności, konfiguracja narzędzi
├── uv.lock                         dokładne wersje i hashe zależności
├── src/screensort_analysis/
│   ├── __init__.py                  oznaczenie pakietu Pythona
│   ├── main.py                     składanie aplikacji HTTP
│   └── api/
│       ├── __init__.py              oznaczenie podpakietu
│       ├── health.py                obsługa GET /health
│       └── schemas.py               kształt danych odpowiedzi
└── tests/test_health.py             testy istniejącego kontraktu
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
3. Wybrać i sprawdzić silnik OCR na kilku przykładach. Dopiero wtedy utworzyć
   `ocr/` z rzeczywistą implementacją, osobno od `api/`.
4. Dodać `vision/` po wyborze modelu analizy wizualnej. Modele ładować raz przez
   lifespan; `/ready` powinien odzwierciedlać rzeczywiście dostępne możliwości.
5. Podłączyć klienta w Javie oraz osobne zadanie analizy; potem prawdziwy wynik
   w React. Nie przerabiać `ScanJob` na zadanie analizy.
6. Dodać opcjonalny backend NVIDIA/CUDA z CPU jako obsługiwanym wariantem.
   Zgodność sterownika, bibliotek i modelu musi być sprawdzona w praktyce.

Nie tworzymy pustych `ocr`, `vision`, `domain`, `infrastructure`, `utils` ani
`shared` na zapas. Nazwy wynikają z funkcji, która rzeczywiście powstaje.
Python nie dostanie repozytoriów JPA ani dostępu do bazy katalogu. Nie przyjmuje
dowolnych ścieżek, nie skanuje dysku i nie zmienia oryginalnych plików.

Nie dodano jeszcze autoryzacji, limitów obrazów, kolejki, modeli, kontenerowego
wdrożenia ani automatycznego startu z Tauri. Publiczne wdrożenie jest poza zakresem
tego szkieletu. Dodanie procesu nie przyspiesza automatycznie skanowania.

## Źródła decyzji

- [FastAPI — większe aplikacje i APIRouter](https://fastapi.tiangolo.com/tutorial/bigger-applications/).
- [FastAPI — testy](https://fastapi.tiangolo.com/tutorial/testing/).
- [uv — projekty i zależności](https://docs.astral.sh/uv/guides/projects/).
- [uv — integracja z FastAPI](https://docs.astral.sh/uv/guides/integration/fastapi/).
