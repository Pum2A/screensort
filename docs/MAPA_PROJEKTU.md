# ScreenSort — mapa projektu (co gdzie jest i jak to działa)

Dokument dla właściciela, który napisał dużo kodu i chce odzyskać orientację. Opis powstał z audytu kodu; szczegółowe problemy są w `PLAN_NAPRAWCZY.md`.

## 1. Idea w jednym zdaniu

Wskazujesz folder ze zrzutami ekranu, ScreenSort go skanuje (nie zmieniając plików), zapisuje katalog w bazie, opcjonalnie rozpoznaje tekst (OCR) i opisuje obraz (model Florence-2), proponuje nazwy i kategorie, a po Twojej akceptacji **kopiuje** pliki do uporządkowanego folderu.

## 2. Trzy programy, które ze sobą rozmawiają

```
 ┌──────────────── Okno aplikacji (Tauri, Rust) ────────────────┐
 │  WebView: React/TypeScript  ── invoke ──►  Rust (pośrednik)   │
 └───────────────────────────────────────────────┬──────────────┘
                 uruchamia i pilnuje (Job Object) │ HTTP 127.0.0.1 + token
        ┌──────────────────┬─────────────────────┴───────────────┐
        ▼                  ▼                                     ▼
  PostgreSQL 18      Backend Java (Spring Boot)  ──HTTP+token──► Serwis Python (FastAPI)
  (prywatny,         skan, katalog, nazwy,                      OCR (RapidOCR) +
  w danych           kategorie, eksport                         opis obrazu (Florence-2)
  użytkownika)       tylko 127.0.0.1                            tylko 127.0.0.1
```

- **React nie zna portów ani tokenów.** Woła tylko dwie komendy Tauri (`backend_request`, `backend_image`), a Rust przepuszcza wyłącznie trasy z listy dozwolonych (`allowed()` w `desktop/mod.rs`, są testy).
- **Rust to „kierownik budowy”**: inicjuje i startuje PostgreSQL, uruchamia backend Java i (jeśli zainstalowany pakiet AI) serwis Python, losuje tokeny przy każdym starcie, pilnuje procesów, obsługuje aktualizacje i instalację pakietu AI.
- **Java ma całą logikę plików**: walidacja folderu, skan, SHA-256, katalog, propozycje, eksport. Źródłowe obrazy nigdy nie są modyfikowane ani usuwane.
- **Python tylko analizuje bajty obrazu** (nie dostaje ścieżek, nie dotyka bazy).

## 3. Mapa katalogów

| Gdzie | Co tam jest |
|---|---|
| `src/main/java/pl/pum/screensort/` | Backend Java (patrz tabela niżej) |
| `src/main/resources/` | `application*.properties` (profile: `desktop`, `local`, `demo`, `observability`), migracje Flyway `db/migration/V1…V9`, stary szablon `templates/scanning/scan.html` (prototyp) |
| `src/test/` | Testy Javy (Testcontainers → potrzebny Docker Desktop) |
| `frontend/src/` | React/TS, foldery „funkcjami” lustrzanymi do backendu |
| `frontend/tests/` | Testy frontendu (`tsc` → `.test-output` → `node --test`), `fixtures/` = podglądy UI |
| `frontend/src-tauri/` | Rust/Tauri: `lib.rs` (start), `updates.rs` (aktualizacje), `desktop/` (procesy, ścieżki, pakiety AI), `tauri.conf.json` (okno, CSP, updater, instalator NSIS), `capabilities/` |
| `analysis-service/` | Python: `launch.py` (start, plik stanu), `main.py` (FastAPI), `api/`, `image/`, `ocr/`, `vision/`, `tests/`, `pyproject.toml` + `uv.lock` |
| `packaging/` | `windows/` (katalog pakietów AI z hashami), `distribution/`, `observability/` |
| `scripts/` | Skrypty PowerShell: budowa instalatora, pakietu AI, podpisywanie aktualizacji, test startu |
| `.github/workflows/` | `ci.yml` (testy), `windows-release.yml` (publikacja wydania) |
| `docs/` | Dokumentacja i dokumenty review (`review-*.md`), ADR-y w `docs/adr/` |
| `.claude/` | Konfiguracja Claude Code: agenci, skille (`db-migration`, `desktop-release`, `review-doc`), hooki formatowania |
| `compose.yaml`, `compose.observability.yaml` | Postgres do **developmentu** (+ Prometheus/Redis) |
| `CLAUDE.md`, `TODO.md`, `PLAN_NAPRAWCZY.md` | Zasady pracy, lista sprzątania po Codexie, plan naprawczy |

### Backend Java — moduły (pakiety `pl.pum.screensort.*`)

| Pakiet | Za co odpowiada |
|---|---|
| `scanning` | Walidacja folderu (`ScanFolderValidator`), skan nierekurencyjny (`ImageScanner`), zadania skanu w pamięci (`ScanJobManager`), REST `/api/scans…`. `scanning/prototype` to stary, martwy prototyp |
| `catalog` | Zapisany katalog plików (`file_record`), odczyt stronicowany, miniatury i podglądy PNG, opcjonalny cache w Redisie |
| `analysis` | Klient HTTP do serwisu Python (`PythonAnalysisClient`), zadanie analizy (jedna inferencja naraz), zapis wyników |
| `naming` | Propozycje nazw i decyzje użytkownika |
| `organization` | Analiza całego folderu (batch), kolejka przeglądu, **eksport kopii** do kategorii (`export/`) |
| `desktop` | Filtr tokenu sesji, cykl życia (plik stanu), przyjmowanie połączenia z serwisem Python |
| `diagnostics` | Metryki produktowe i **redakcja wyjątków** (`RedactedThrowable`) — tu jest wzorzec „nie loguj ścieżek” |

### Frontend — foldery

`scanning` (skan i postęp), `catalog` (galeria, podgląd, szczegóły), `analysis` (panel analizy), `naming` (nazwy), `organization` (+ `review/`, `export/`), `desktop` (start powłoki, pakiet AI, aktualizacje, `transport.ts` = most do Rusta), `workspace` (nawigacja, stan), `ui` (wspólne klocki: modal, tooltip, ikony).

## 4. Typowy przepływ

1. Start: Rust uruchamia Postgresa → Javę → (opcjonalnie) Pythona; ekran startowy czeka na `backendReady`.
2. „Wybierz folder” → natywny dialog Tauri → ścieżka trafia do backendu.
3. „Skanuj” → Java waliduje folder, liczy SHA-256, zapisuje `file_record` (skan przyrostowy po rozmiarze i czasie modyfikacji).
4. Galeria czyta katalog stronami (24 elementy), miniatury po ID rekordu.
5. Analiza: Java wysyła bajty obrazu do Pythona z tokenem; Python zwraca OCR i opis; wynik ląduje w bazie.
6. Propozycje nazw i kategorii → kolejka przeglądu → Twoja akceptacja → eksport **kopii** do nowego folderu.

## 5. Zasady, których pilnuje projekt (z `CLAUDE.md`)

Prywatność (żadnych ścieżek, nazw, SHA-256, tekstu OCR w logach/odpowiedziach — **tu API dziś odstępuje od reguły, patrz #1 w planie**), logowanie tylko przez SLF4J/`logging`, brak `any` i `@ts-ignore`, brak `unwrap()` w Rust poza testami, zastosowanych migracji Flyway nie wolno edytować.

## 6. Jak to odpalić i sprawdzić

Komendy są w `CLAUDE.md` (sekcja Commands). W skrócie: Java `./mvnw -q test -Dtest=Klasa#metoda` (Docker musi działać), frontend `npm run lint`, `npm run build`, `npm test` w `frontend/`, Python `uv run --no-sync pytest` w `analysis-service/`.

## 7. Gdzie jest „bałagan po Codexie”

Notatki robocze w `ScanWorkflow.java` i `KnownFilesReader.java`, prototyp Thymeleaf, kilka duplikatów (SHA-256, sprawdzanie ścieżek), nieaktualne README/dokumenty. Pełna lista: `TODO.md` i `PLAN_NAPRAWCZY.md`.
