# ScreenSort — stan projektu i punkt dalszej pracy

Sprawdzone 2026-10-21. Wersja desktopu: **0.2.3 preview**.
To mapa aktualnego kodu, nie deklaracja gotowości produkcyjnej ani pełny audyt bezpieczeństwa.

## Co stało się po PR

PR [#1](https://github.com/Pum2A/screensort/pull/1) scalił zapis uporządkowanych
kopii do `codex/on-demand-ai-and-batch-review`. Commit scalenia to `f12e5f7`.
Drzewo jego plików jest identyczne jak w końcowym commicie PR `b10b5f1` — samo
scalenie nie dodało ani nie usunęło funkcji. `main` pozostawał wtedy 37 commitów
z tyłu; ta synchronizacja przenosi gotowy etap na wspólną gałąź główną.

Przyjęty obieg pracy: aktualny `main` → krótka gałąź zmiany → PR do `main` →
testy i przegląd → scalenie. Nie potrzebujemy stałego `develop` ani PR-ów
zależnych od kolejnych gałęzi roboczych. Release jest osobnym, wersjonowanym
artefaktem: merge kodu nie instaluje go na komputerze użytkownika.

## Co użytkownik może rzeczywiście zrobić

```text
Tauri uruchamia prywatną bazę i backend Java
  → wybór folderu → walidacja → przyrostowy skan → katalog w PostgreSQL
  → pobranie/uruchomienie opcjonalnego silnika analizy
  → Analizuj wszystko → Java odczytuje źródło → lokalny Python: OCR + caption
  → wyniki w PostgreSQL → propozycje nazw/kategorii → poprawki i zatwierdzenie
  → Zapisz i uporządkuj → podgląd zatwierdzonych decyzji → wybór miejsca
  → osobny folder ScreenSort → kategorie → zweryfikowane kopie + postęp
```

Katalog nie wymaga uruchomionego modelu. Python dostaje bajty obrazu, nie dostęp
do bazy ani dowolne ścieżki. Baza desktopu jest oddzielona od bazy developerskiej
z Dockera. Skan dotyczy bezpośrednich wpisów wybranego folderu, nie rekursji po dysku.

| Obszar | Działa | Ważna granica |
| --- | --- | --- |
| Skanowanie | Walidacja, bounded workers, SHA strumieniowo, mapa znanych plików, format i wymiary obrazu z nagłówka | Rozmiar i data pozwalają pominąć pracę, ale nie dowodzą identyczności bajtów |
| Katalog | Trwałe rekordy, paginacja, miniatury po ID i większy podgląd | Wyszukiwanie frontendu filtruje bieżącą stronę, nie cały katalog |
| OCR i wizja | RapidOCR i Florence-2-base-ft CPU, osobne wyniki i błędy | Nie ma wyboru innych modeli ani GPU; caption nie gwarantuje rozpoznania gry/obiektu |
| Analiza folderu | Checkpointy, pauza, wznowienie, odzyskanie po restarcie | Ponowny job nie ma wersjonowanego cache inferencji |
| Nazwy i kategorie | Edycja, zatwierdzenie/pominięcie, ochrona przed nieaktualnym formularzem | Nazwa to do 6 słów caption; kategoria to słowa kluczowe OCR/caption, nie interpretator semantyczny |
| Organizacja | Kopie zatwierdzonych plików w 5 kategoriach, kolizje nazw, status i problemy | Nie przenosi ani nie usuwa oryginałów; przerwany eksport nie wznawia się w połowie |
| Dystrybucja | Core bez Pythona, opcjonalne pobranie pakietu, podpisany updater | Podpis updatera to nie Windows Authenticode; nie ma tu nowego testu czystej VM |
| Metryki/cache | Micrometer, profil Prometheus, opcjonalny Redis podglądów | Narzędzia developerskie; Redis nie zastępuje PostgreSQL i nie przyspiesza inferencji |

## Najważniejsze braki wynikające z kodu

### Trafność nazw — to jeszcze nie jest docelowa analiza kontekstowa

[`NameSuggestionGenerator`](../src/main/java/pl/pum/screensort/naming/NameSuggestionGenerator.java)
usuwa częste słowa z caption i skleja maksymalnie sześć kolejnych słów w nazwę.
[`NamingService`](../src/main/java/pl/pum/screensort/naming/NamingService.java)
przekazuje mu opis wizualny, nie tekst OCR. Dlatego poprawnie odczytany napis
„Roblox” nie staje się automatycznie kontekstem proponowanej nazwy.
[`CategorySuggester`](../src/main/java/pl/pum/screensort/organization/CategorySuggester.java)
korzysta z obu tekstów, ale tylko przez dopasowanie słów kluczowych.

Python w [`ImageAnalyzer`](../analysis-service/src/screensort_analysis/image/analyzer.py)
zbiera OCR i wizję w jednej odpowiedzi, lecz nie prowadzi ich wspólnej interpretacji.
[`VisionAnalyzer`](../analysis-service/src/screensort_analysis/vision/analyzer.py)
używa `<CAPTION>` i CPU, a nie porównywania hipotez, treningu pod gry czy top-3
semantycznych propozycji. Zmiana widoku lub dopisanie reguły „cartoon = Roblox”
nie naprawi tego wiarygodnie. Plan: [plan-contextual-analysis.md](plan-contextual-analysis.md).

### Start silnika — ponowna próba jest ograniczona

[`Owner::start_analysis`](../frontend/src-tauri/src/desktop/processes.rs)
ustawia `analysis_attempted` przed uruchomieniem procesu; kolejna próba w tej
sesji zwraca komunikat o restarcie aplikacji. Readiness modeli ma limit 180 s.
[`load_analyzer`](../analysis-service/src/screensort_analysis/image/runtime.py)
ładuje oba modele i wykonuje warm-up przed gotowością. Nie ma szczegółowego
raportowania czasu importów, OCR, wizji i próbnego przebiegu. Kod potwierdza
ograniczenie, ale bez pomiaru konkretnego uruchomienia nie rozstrzyga, który etap
spowalnia komputer właściciela.

### Eksport — działające kopie, nie pełny menedżer odzyskiwania

[`OrganizedFileCopier`](../src/main/java/pl/pum/screensort/organization/export/OrganizedFileCopier.java)
czyta źródło jednym buforem 64 KiB, liczy SHA i zapisuje plik tymczasowy. Dopiero
po kontroli zgodności udostępnia nazwę końcową, bez nadpisywania istniejącego pliku.
[`ExportStore`](../src/main/java/pl/pum/screensort/organization/export/ExportStore.java)
zachowuje migawkę decyzji i status każdego pliku. Restart oznacza przerwane zadanie
jako `INTERRUPTED`; następny eksport tworzy nowy folder, nie uzupełnia poprzedniego.
Nagłe zabicie procesu może zostawić `.part`. Brakuje kontroli wymaganej pojemności
przed kopiowaniem; błędy zapisu są obsługiwane w trakcie pracy.

## Przegląd niezawodności backendu

Przegląd kodu backendu z 2026-10-21 poprawił cztery rzeczy: wyciek ścieżek
i hashów do konsoli, odpowiedź HTTP 500 zamiast 409 przy wznowieniu analizy
folderu, wyciek wątków klienta HTTP do Pythona oraz brak blokady pliku przy
eksporcie. Skan rozpoznaje obraz z nagłówka, więc nie dekoduje go w całości,
a eksport zajmuje plik w tej samej transakcji, która go wybiera. Dokładny
zakres, zmienione zachowanie i lista braków, które zostały w backlogu:
[review-backend-quality.md](review-backend-quality.md).

## Co sprawdzono w tej synchronizacji

- CI po scaleniu `f12e5f7`: Java, frontend, Python i Windows desktop — sukces.
- Frontend lokalnie: 62/62 testy, lint, build i typowanie scenariuszy preview.
- Rust lokalnie: 22/22 testy. Testy localhost/przenoszenia folderów uruchomiono
  poza sandboxem, który blokował te operacje; nie osłabiano testów ani uprawnień aplikacji.
- Python lokalnie: 102 zaliczone, 2 opt-in testy rzeczywistego OCR/wizji pominięte;
  lint poprawny. TestClient wymagał uruchomienia poza ograniczeniami sandboxa.
- Java lokalnie: 17/17 testów `ReviewJpaTest`, `OrganizedFileCopierTest`,
  `DesktopUpdateReadinessTest`, na izolowanej bazie PostgreSQL i testowych plikach.
- Potwierdzono opublikowane wydanie 0.2.3 i manifest kanału `updates-preview`.
- Przegląd niezawodności backendu: 167/167 testów jednostkowych i 25/25 testów
  bazodanowych (PostgreSQL 18.6 przez Testcontainers), w tym pełny przepływ
  eksportu HTTP → PostgreSQL → dysk. Nowa migracja `V9` została zastosowana
  na świeżej bazie.

To nie jest świeży benchmark modeli ani ręczny test całego interfejsu. Nie
zmieniano prywatnej bazy, zdjęć ani zainstalowanego programu. Serwer fixture
`workspace-preview.html` używa danych w pamięci, nie wykonuje prawdziwego eksportu.
Wyniki standardowych testów API nie świadczą o jakości opisów na dowolnych zdjęciach.

## Najbliższy sensowny etap

Najpierw naprawić cykl życia silnika: rzeczywiste etapy startu, timeout, sprzątanie
wyłącznie własnego procesu i ponowna próba bez zamykania katalogu. Sprawdzić
przerwanie/wznowienie analizy folderu bez utraty checkpointów. Dopiero potem
oceniać wspólną interpretację OCR/wizji na małym zestawie syntetycznych lub
uprawnionych przykładów, z mierzalną jakością nazw, czasem i RAM. Nie dodawać
brokera ani większego modelu jako zamiennika diagnozy.

Dokładny przegląd eksportu: [review-organized-export.md](review-organized-export.md).
Uruchomienie dla developera: [development.md](development.md).
