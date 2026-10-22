# Plan wdrożenia: kontekstowe nazywanie zrzutów i uczenie się z decyzji

Stan: propozycja, 2026-10-22. Nic z tego planu nie jest jeszcze w kodzie aplikacji.
Źródło: prototyp na 78 obrazach testowych (`testset/`, poza gitem) i mapa miejsc w kodzie od subagenta `explorer`.

## 1. Cel i zasady

- Nazwa ma powstawać z **treści ekranu** (OCR z pozycjami linii), **typu sceny** (mały model CLIP) i **wiedzy o popularnych markach**, a nie tylko z opisu Florence-2.
- Działa na zwykłym komputerze: CLIP ViT-B/32 int8 to ok. 157 MB i ok. 55 ms na obraz na CPU (pomiar na i5-10400F).
- Aplikacja **uczy się tylko z decyzji danego użytkownika**, lokalnie. Nic nie wychodzi z komputera.
- Nauczona reguła **nigdy nie zmienia nazwy sama**. Zawsze jest tylko propozycją w przeglądzie.
- Python zostaje **bezstanowy** (bez bazy). Pamięć decyzji i reguły żyją w Javie i PostgreSQL.
- **Jakość od pierwszego uruchomienia, bez nauki**: cel to 70–80% trafnych nazw wśród pokazanych. Niepewne zrzuty trafiają do „Do przejrzenia” zamiast dostać zgadniętą nazwę. Uczenie się z decyzji to dodatek, nie fundament.
- **Minimum sprzętowe** (decyzja właściciela, 2026-10-22): pełna analiza od 4 rdzeni z AVX2 (Intel 4. generacji / 2013+). Starsze procesory dostają tryb „tylko OCR + reguły + katalog”. Skan w tle z niskim priorytetem procesu i limitem wątków.
- **Pomiar**: jakość mierzy narzędzie testowe na znanym zestawie (etap 0); szybkość i zasoby mierzy Prometheus (profil `observability`, już w projekcie).

## 2. Przepływ docelowy

```
obraz ─► Python: OCR + pozycje linii ─► wskazówki (tytuł, marka, typy z pewnością) ─┐
     └─► Python: CLIP wektor + top-k typów ──────────────────────────────────────┘
                                                                                  ▼
Java: zapis wyniku ─► składanie nazwy + reguły użytkownika + podobne zrzuty ─► 3 propozycje
                                                                                  ▼
React: przegląd z chipami ─► decyzja użytkownika ─► Java: zapis decyzji ─► nauka reguł
```

## 3. Etapy (każdy to osobny PR z testami)

### Etap 0. Narzędzie pomiarowe w repo
Bez tego nie wiemy, czy kolejne etapy poprawiają wynik.
- `analysis-service/tools/evaluate_naming.py` (nowy): przenosi skrypty prototypu (`baseline.py`, `clip_fair.py`, `fusion.py`, `compose_v3.py`) w jeden skrypt z funkcjami `run_pipeline(images)`, `score(names, gold)`. Folder z obrazami podawany parametrem, obrazy nie trafiają do repo.
- `testset/` zostaje w `.gitignore`.

### Etap 1. Python: OCR zwraca pozycje linii
- `analysis-service/src/screensort_analysis/api/schemas.py`: nowa klasa `OcrLine(text, score, x0, y0, x1, y1)` (współrzędne znormalizowane 0–1), pole `lines: list[OcrLine]` w `OcrResult` (dziś `OcrResult` ma tylko `text`, linia 41).
- `ocr/recognizer.py`, `recognize_prepared` (linia 45): oprócz `output.txts` mapować `output.boxes` i `output.scores` na `OcrLine`. Dziś pozycje są odrzucane w linii 58.
- Testy: `tests/test_ocr_*.py` z fałszywym silnikiem zwracającym boxy.
- Prywatność: linie OCR to ten sam tekst co dziś, tylko z pozycją. Nadal nie wolno go logować.

### Etap 2. Python: wektor obrazu i typ sceny (CLIP)
- Nowy moduł `vision/clip.py`: klasa `ClipEmbedder` z metodami `embed(image) -> list[float]` (512 liczb) i `rank(vector, top_k=3) -> list[TypeScore]`. Używa `onnxruntime` (już jest w zależnościach przez RapidOCR) i tokenizera z `tokenizers`.
- Nowy plik danych `vision/vocabulary.json`: hasła angielskie dla CLIP plus polski slug (`"a first-person shooter game" -> "gra_fps"`). Teksty haseł kodowane raz przy starcie.
- `image/runtime.py`, `load_analyzer` (linia 25): ładowanie `ClipEmbedder` obok OCR i Florence, rozgrzanie na białym obrazie jak dziś.
- `image/analyzer.py`, `ImageAnalyzer.analyze` (linia 39): nowy krok `semantic` ze statusem COMPLETED/FAILED/SKIPPED, jak OCR i visual.
- `api/schemas.py`: `SemanticResult(embedding, types: list[TypeScore], model, model_revision)` w `ImageAnalysisResult` (linia 111).
- Pakiet AI: `packaging/windows/ai-packages.json` (`requiredFiles`) i `scripts/build-ai-package.ps1` (`$required`, linie 16–19) dopisują pliki ONNX. To wymaga przebudowy pakietu i wydania (skill `desktop-release`).

### Etap 3. Python: wskazówki do nazwy
- Nowy moduł `naming/hints.py` z funkcjami z prototypu: `drop_overlays(lines)`, `window_title(lines)`, `title_from_lines(lines)`, `find_brand(lines, title)`, `lexicon(text)`, `fuse(types, lexicon)`.
- Nowy plik danych `naming/catalog.json`: mały katalog popularnych marek, aplikacji i gier z typem (ok. 100 pozycji).
- Wynik `NamingHints(title, title_source, brand, types: list[TypeScore], confidence, rules_version)` dokładany do odpowiedzi.
- Python nie składa finalnej nazwy. Daje fakty, a decyzję podejmuje Java, bo tylko ona zna reguły użytkownika.

### Etap 4. Java: nowe pola i składanie nazwy
- `analysis/result/AnalysisResult.java` (rekord, linia 8): opcjonalne `semantic` i `hints`. **Najpierw test**, że stary JSON bez tych pól i nowy JSON z nimi deserializują się poprawnie. Nie sprawdzono jeszcze, czy `JsonMapper` w `PythonAnalysisClient` (linia 80) ignoruje nieznane pola, więc kolejność wdrożenia to Java przed Pythonem.
- `naming/NameSuggestionGenerator.java`: nowa metoda `suggest(AnalysisResult, originalName) -> List<NameSuggestion>` (3 propozycje), składająca `{marka_}{tytuł}_{typ}` przez `FileNameRules.problem` (linia 16). Stara `suggest(originalName, description)` (linia 22) zostaje jako zapas, gdy brak wskazówek.
- `FileNameRules` dopuszcza Unicode (zabronione tylko `<>:"/\|?*` i znaki sterujące, linia 10), więc nazwy typu `東京_encyklopedia.png` są poprawne i nie trzeba transliteracji.
- `organization/CategorySuggester.java` (linia 12): mapowanie slugów typu na `ImageCategory`. Rozszerzenie enumu (dziś PROGRAMMING, DOCUMENTS, GAMES, PHOTOGRAPHS, OTHER) wymaga nowej migracji, bo `file_organization_review` ma CHECK na kategorie (V7). Zmiana `VERSION` z `caption-keywords-v1` na `hints-v1`.
- `organization/BatchStore.java`, `completed` (linia 90): wywołanie nowego generatora. Jest w `@Transactional`, ale składanie nazwy to czysta logika bez inferencji, więc transakcji nie wydłuża.

### Etap 5. Java + baza: pamięć decyzji i reguły użytkownika
- Migracja `V10__create_naming_feedback.sql` (najnowsza dziś to V9; skill `db-migration`):
  - `naming_feedback`: `file_record_id`, `analysis_id`, `embedding real[]`, `title_key`, `brand`, `suggested_name`, `final_name`, `outcome` (ACCEPTED, EDITED, REJECTED), `decided_at`.
  - `naming_rule`: `kind` (BRAND, TITLE), `rule_key`, `type_slug`, `name_pattern`, `confirmations`, `rejections`, `active`, `updated_at`.
- Nowy pakiet `naming/learning/`:
  - `FeedbackRecorder.record(...)`: wołany z `ReviewService.save` (linia 31) i `NamingService.approve` (linia 39), czyli tam, gdzie dziś zapisuje się decyzja.
  - `NamingRules.match(hints)` i `NamingRules.update(feedback)`: reguła włącza się po 2 zgodnych decyzjach, traci wagę przy odrzuceniu, wygasa po 3 odrzuceniach.
  - `SimilarScreens.nearest(embedding, k)`: podobieństwo kosinusowe w pamięci po decyzjach użytkownika (limit np. 5 000 ostatnich). Bez pgvector, bo baza jest prywatna i mała.
- Zabezpieczenia: reguły tylko podpowiadają; każda propozycja ma źródło (`OCR`, `CLIP`, `KATALOG`, `TWOJA_REGUŁA`, `PODOBNY_ZRZUT`).

### Etap 6. Frontend i most Tauri
- `frontend/src/organization/review/ReviewEditor.tsx` (linia 17) i `state.ts`: 3 propozycje jako chipy, krótkie „dlaczego” (np. „z paska tytułu okna”, „Twoja reguła: Roblox → gra”), edycja jak dziś.
- `frontend/src/naming/NamingPanel.tsx` (linia 11) i `naming/types.ts`: te same chipy dla pojedynczego obrazu.
- Nowy ekran „Twoje reguły” (`frontend/src/naming/rules/`) z listą i przyciskiem „zapomnij”.
- Java: `naming/api/NamingRulesController` z `GET /api/naming/rules` i `DELETE /api/naming/rules/{id}`.
- Rust: `frontend/src-tauri/src/desktop/mod.rs`, `allowed()` (linia 347): nowe trasy plus testy `bridge_*` obok istniejących (linia 470+).

### Etap 7. Pomiar w produkcie (bez danych osobowych)
- `diagnostics/ProductMetrics.java`: licznik `screensort.naming.suggestion` z tagami `outcome` (accepted, edited, rejected) i `source` (ocr, clip, catalog, rule, similar). Bez nazw, tytułów i tekstu OCR, zgodnie z zasadą prywatności.

### Etap 8. Wydanie
- Przebudowa pakietu AI z plikami CLIP, nowa wersja aplikacji, skill `desktop-release`.

## 4. Ryzyka i otwarte decyzje

| Temat | Ryzyko | Decyzja / sposób |
|---|---|---|
| Nieznane pola JSON w Javie | Python z nowymi polami może wywrócić starą Javę | test deserializacji, wdrożenie Java przed Pythonem |
| Rozmiar pakietu AI | +ok. 157 MB | akceptowalne; CLIP opcjonalny, OCR działa bez niego |
| Złe nauki | przypadkowa decyzja tworzy złą regułę | próg 2 potwierdzeń, wygaszanie, ekran „zapomnij” |
| Kategorie | enum i CHECK w V7 ograniczają typy | nowa migracja przy rozszerzeniu, nie edycja V7 |
| Jakość na prawdziwych zrzutach | prototyp testowany głównie na stronach WWW | etap 0 i test na zrzutach właściciela przed etapem 4 |
| Florence-2 | wolny (ok. 5 s/obraz) i mało przydatny dla nazw | zostaje na razie jako opis pomocniczy; decyzja o usunięciu po etapie 4 |

## 5. Kolejność i rozmiar

Etap 0 → 1 → 3 → 4 dają już lepsze nazwy bez CLIP i bez nauki (najmniejsze ryzyko).
Etap 2 dokłada sceny bez tekstu. Etap 5 i 6 to uczenie się i nowy przegląd. Etapy 7–8 zamykają wdrożenie.
