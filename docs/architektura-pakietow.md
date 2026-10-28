# Pakiety backendu: nazwy zgodne z tym, co widzi użytkownik

Zasada: folder w kodzie nazywa się tak jak krok w aplikacji. W środku pliki leżą płasko; podfolder dopiero,
gdy jest dla niego osobne, jasne zadanie.

## Docelowy kształt

| Pakiet | Krok w aplikacji | Co w nim jest | Skąd | Status |
|---|---|---|---|---|
| `gallery` | „Dodaj folder” | dodane foldery, spis zdjęć, lista bibliotek | `catalog`, `library` | ✅ backend #45, frontend PR B |
| `gallery/reading` | wczytywanie folderu (pasek postępu) | szukanie obrazów, hash, zadania w tle | `scanning` (10 podfolderów) | ✅ PR A |
| `gallery/preview` | miniatury na kafelkach | dekodowanie, cache na dysku i w Redisie | `catalog/preview` | ✅ PR A |
| `analysis` | „Uporządkuj z AI” | analiza zdjęć i partie (`Batch*`); klient Pythona w `engine/` | `analysis` (6 podfolderów) + partie z `organization` | ✅ PR E |
| `suggestions` | propozycje na kafelkach | nazwa i kategoria z wyniku AI, język nazw PL/EN | `naming` + `CategorySuggester` + `NamingLanguageController` | ✅ PR E |
| `review` | „Przejrzyj” | zatwierdzanie i poprawki; na razie też `ImageCategory` i `ApprovedFilesSql` | `organization/review` | ✅ PR C |
| `export` | „Zapisz uporządkowane” | kopiowanie do folderów kategorii | `organization/export` | ✅ PR D |
| `tauri` | — | współpraca z aplikacją desktopową | `desktop` | ⬜ |
| `errors` | — | format błędów API, czyszczenie błędów z prywatnych ścieżek | `api` + `RedactedThrowable` | ⬜ |
| `files` | — | nazwy plików, formaty, hash | `files` | bez zmian |
| `metrics` | — | liczniki i stopery dla Prometheusa | `diagnostics` | ⬜ |

## Frontend (`frontend/src`)

Te same nazwy co w backendzie, więc `gallery` w Javie i `gallery` we froncie to dwie połówki tej samej rzeczy.

| Folder | Co w nim jest | Skąd | Status |
|---|---|---|---|
| `app` | `App`, `main`, menu boczne, nagłówek, globalne style | `App.tsx`, `main.tsx`, `workspace/` | ✅ PR B |
| `gallery` | kafelki, duplikaty, miniatury | `catalog/`, `library/` | ✅ PR B |
| `gallery/reading` | pasek wczytywania folderu | `scanning/` | ✅ PR B |
| `gallery/details` | okno szczegółów jednego zdjęcia | `catalog/ImageDetails*` | ✅ PR B |
| `analysis` | „Uporządkuj z AI”, postęp partii | `analysis/`, `organization/OrganizationPanel`, `api`/`types` → `batchApi`/`batchTypes` | ✅ PR E |
| `suggestions` | propozycje nazw, przełącznik PL/EN | `naming/` | ✅ PR E |
| `review` | przegląd i zatwierdzanie | `organization/review/` | ✅ PR C |
| `export` | zapis do folderów kategorii, podgląd planu | `organization/export/`, `OrganizationPlanPreview` | ✅ PR D, E |
| `tauri` | start, pakiety AI, aktualizacje | `desktop/` | ⬜ |
| `http` | wspólne zapytania do backendu | `shared/` | ⬜ |
| `ui` | przyciski, ikony, okna | — | bez zmian |

Dalej: jeden krok użytkownika na PR, backend i frontend naraz (np. „review” przenosi `organization/review`
w Javie i we froncie w tym samym PR).

## Jak przenosimy

- Jeden PR na jeden wiersz tabeli (albo kilka małych naraz). Każdy PR tylko przenosi pliki i poprawia importy —
  bez zmian w logice — więc te same testy muszą przejść przed i po.
- Adresy API (`/api/...`) się nie zmieniają, więc frontend i Tauri nie wymagają zmian.
- Nazwy klas (np. `ScanRestController`, `CatalogReader`) zostają na razie stare. Zmienimy je w osobnej rundzie,
  razem z ekranem: przycisk „Skanuj” znika, dodanie folderu od razu wczytuje zdjęcia.
- Łączenie drobnych klas w jedną (np. pięć małych rekordów wyniku wczytywania) — też osobno, po przenosinach.
