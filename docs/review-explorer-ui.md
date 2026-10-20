# ScreenSort — UI eksploratora do weryfikacji

Etap z 20 października 2026, gałąź `codex/on-demand-ai-and-batch-review`.
To implementacja istniejącego frontendu, nie nowy backend i nie nowy model.
Zainstalowana aplikacja `.exe` nie została w tym etapie zaktualizowana.

## Co zmienia się w pracy użytkownika

Start pokazuje dwa etapy, które da się wywnioskować z obecnego statusu Tauri:
przygotowanie lokalnej bazy oraz uruchomienie katalogu. Nie udajemy procentowego
postępu ładowania modeli. Silnik analizy pozostaje uruchamiany osobno, na żądanie.
Stan błędu jest czerwony; oczekiwanie ma animowany wskaźnik i czas oczekiwania.

Po wejściu do aplikacji są dwa odrębne widoki: **Katalog obrazów** oraz
**Analiza i propozycje**. Katalog domyślnie pobiera 24 opisy na stronę, a nie 50.
Miniatury zachowują istniejące lazy loading. Wyszukiwanie filtruje tylko bieżącą
stronę — nie udaje wyszukiwarki całej bazy. Można przełączyć siatkę na gałęzie
PNG/JPEG. To grupy formatów, nie wymyślone podfoldery ani kategorie AI.
Kliknięcie wybiera obraz do inspektora; dwuklik lub „Powiększ obraz” otwiera
większy podgląd. Escape i krzyżyk zamykają go z animacją i przywróceniem fokusu.

Analiza całego folderu nadal używa istniejących zadań Javy. „Wznów analizę”
kontynuuje przerwane zadanie, zamiast domyślnie uruchamiać je od zera. „Analizuj
wszystko” obejmuje cały zapisany folder, nie tylko 24 widoczne miniatury.
Przegląd propozycji nadal pobiera strony po 50. Kategorie i liczniki w drzewku
dotyczą wyłącznie pobranej strony; jest to jawnie opisane w interfejsie.

Wybrany obraz ma jeden formularz nazwy i kategorii. Można rozwinąć zapisany OCR
i opis wizualny, żeby ocenić propozycję. Odczyt tych danych następuje dopiero
po rozwinięciu sekcji. Pokazujemy je tylko wtedy, gdy identyfikator analizy
i SHA obrazu zgadzają się z propozycją. Nie uruchamia to ponownej inferencji.

Przełączanie katalog ↔ analiza zachowuje roboczy formularz. Zmiana pliku lub
filtra wymaga jawnego odrzucenia niezapisanej edycji. Zmiana folderu/skan są
zablokowane podczas edycji lub zapisu. Konflikt zapisu zachowuje tekst formularza;
użytkownik sam decyduje o wczytaniu aktualnej wersji. Zatwierdzanie nadal zapisuje
wyłącznie decyzję w bazie. Nie zmienia i nie przenosi pliku na dysku.
Po zakończonej analizie można otworzyć „Zapisz i uporządkuj”. Pokazuje on
stronami po 50 tylko aktualne, zatwierdzone decyzje: ścieżkę źródłową oraz
przewidywane `ScreenSort/<kategoria>/<zatwierdzona-nazwa>`. Wybór folderu
docelowego pozwala przygotować kopię. „Zapisz uporządkowaną kopię” uruchamia
zadanie w Javie i pokazuje jego postęp. Każdy eksport otrzymuje nowy folder
`ScreenSort`, `ScreenSort (2)` itd. Oryginały pozostają w swoim miejscu.

## Gdzie przechodzi kod

```text
main.tsx → MotionProvider → DesktopStartup → App
                              │                ├─ ExplorerSidebar
                              │                ├─ CatalogGallery → CatalogDetailsPanel
                              │                │                    └─ ImageDetailsDialog
                              │                └─ OrganizationPanel
                              │                    └─ ReviewQueue
                              │                        ├─ ReviewCategoryTree
                              │                        ├─ ReviewEditor → ReviewEvidence
                              │                        └─ OrganizationPlanPreview → GET /batches/{id}/plan
                              └─ DesktopRuntime → DesktopAnalysisControls
                                                  └─ AiPackagePanel → AnalysisEngineContent
```

| Miejsce w `frontend/src/` | Odpowiedzialność i powód wydzielenia |
| --- | --- |
| `App.tsx`, `workspace/ExplorerSidebar.tsx` | Nawigacja, folder, skan, strona katalogu. `organizationVisited` odkłada pierwszy odczyt propozycji do wejścia w ten widok, a później zachowuje go pod `hidden`, by nie kasować edycji. |
| `workspace/state.ts` | Czyste funkcje filtrowania jednej strony, grupowania formatów i bezpiecznego odczytu zapamiętanego folderu. Można je testować bez przeglądarki. |
| `catalog/CatalogGallery.tsx` | Stan zaznaczenia, wyszukiwanie lokalne, siatka/gałęzie i otwarcie podglądu. Bez nowych zapytań skanujących dysk. |
| `ui/MotionProvider.tsx` | Jeden punkt konfiguracji Motion. `LazyMotion` + `domAnimation` bez pełnego pakietu gestów/przeciągania. Respektuje systemowe ograniczenie ruchu. |
| `ui/Modal.tsx`, `catalog/ImageDetailsDialog.tsx` | Natywny `dialog`, klawiatura, fokus, blokada przewijania tła i animacja wyjścia przez `AnimatePresence`. |
| `desktop/StartupScreen.tsx`, `status.ts` | Prezentacja istniejącego statusu startu. Nie sterują procesem PostgreSQL ani nie naprawiają samego startu modeli. |
| `desktop/runtimeContext.ts`, `DesktopAnalysisControls.tsx` | Przekazanie stanu Tauri do bocznego panelu; ustawienia silnika w osobnym oknie. |
| `desktop/packages/AiPackagePanel.tsx` | Prawdziwe wywołania pobierania/importu/anulowania i odpytywanie statusu. Polecenia Rusta pozostają te same. |
| `desktop/packages/AnalysisEngineContent.tsx` | Sama prezentacja pobierania, błędu, gotowości i przygotowania modeli. Można bezpiecznie oglądać te stany bez instalacji silnika. |
| `organization/OrganizationPanel.tsx` | Rzeczywisty stan istniejącego zadania, opcje analizy, pauza/wznowienie i pobieranie stron propozycji. |
| `organization/OrganizationPlanPreview.tsx` | Podgląd paginowanego planu zatwierdzonych nazw i kategorii oraz wybór miejsca zapisu. |
| `organization/export/OrganizationExport.tsx` | Zlecenie kopiowania, odczyt zapisanego postępu, błędy i folder wynikowy. |
| `organization/review/ReviewQueue.tsx`, `filters.ts` | Wybór pliku, filtry jednej strony i blokada utraty roboczej edycji. |
| `organization/review/ReviewEditor.tsx` | Istniejący zapis decyzji z wersjami formularza i SHA; dodano czytelniejszą prezentację, nie zmieniono kontraktu zapisu. |
| `organization/review/ReviewEvidence.tsx` | Odczyt źródeł propozycji na żądanie; anulowanie żądania po zamknięciu i ochrona przed pokazaniem innej analizy. |

Styl jest rozdzielony według komponentów: moduły CSS dla katalogu i analizy,
osobny `OrganizationPanel.css` dla przeglądu oraz globalne tokeny/układ.
Formularze korzystają z już obecnego Tailwind 4, ikony z Lucide.
Nie dołożono biblioteki grafów ani zdalnych fontów. Samo użycie Tailwinda
nie przyspiesza inferencji ani skanu — to narzędzie do budowania interfejsu.

## Jak sprawdzić bez bazy i modeli

W katalogu `frontend` uruchom `npm run workspace:preview`, a następnie otwórz:

`http://127.0.0.1:5184/tests/fixtures/workspace-preview.html`

Żółty pasek oznacza dane fikcyjne. To rzeczywiste komponenty aplikacji połączone
z testowym API w pamięci. Obrazy są syntetycznymi ilustracjami z lokalnego serwera;
nie są zdjęciami właściciela. Zapis znika po odświeżeniu strony. Przyciski w pasku
pozwalają obejrzeć start, pauzę 7/94, pobieranie silnika, ładowanie i błąd.
Tego pliku wejściowego nie ma w produkcyjnym `dist/index.html`.

Proponowana weryfikacja:

- Obejrzyj katalog jako siatkę i jako gałęzie; wpisz fragment nazwy, przejdź na drugą stronę.
- Wybierz obraz, otwórz podgląd, zamknij Escape; fokus powinien wrócić do miniatury.
- W analizie wpisz własną nazwę, przejdź do katalogu i wróć. Nazwa ma pozostać.
- Zmień plik podczas edycji; sprawdź obie decyzje ostrzeżenia przed utratą zmian.
- Rozwiń OCR i opis, zatwierdź nazwę i kategorię, sprawdź odpowiedź zapisu.
- Dla pierwszego pliku użyj testowego „Symuluj konflikt #1” przed zapisem: błąd ma być czerwony, wpisany tekst zachowany.
- Otwórz scenariusz pauzy, wznów i ponownie wstrzymaj. Licznik nie powinien wrócić do zera.
- Sprawdź szerokość 390 px i systemowe ograniczenie animacji.

Rzeczywisty frontend developerski uruchamia się dotychczasową komendą
`npm run tauri dev` z przygotowanym lokalnym runtime. Podgląd fikcyjny nie jest
testem uruchomienia nowego instalatora ani prawdziwego rozgrzewania modeli.

## Sprawdzenia wykonane w tym etapie

`npm test`: 62/62; `npm run build`, `npm run lint` i `npm run check:previews` przeszły lokalnie.
16 testów Javy przeszło z izolowanym PostgreSQL uruchomionym przez
`scripts/verify-native-tests.ps1`. Obejmuje to HTTP → baza → rzeczywista kopia pliku.

W poprzednim etapie manualnie sprawdzono na fikcyjnym API: zachowanie edycji między widokami,
potwierdzenie jej odrzucenia, zapis/konflikt, źródła OCR/wizji, filtr błędów,
stronicowanie 25–48/94, podgląd/Escape/fokus, pauzę/wznowienie oraz układ 390 px
bez poziomego wyjścia poza ekran. Weryfikacja nie jest benchmarkiem, audytem
pełnej dostępności ani dowodem szybszego ładowania modeli. Nowy panel eksportu
przeszedł sprawdzanie typów i testy API; automatyczna kontrola jego wyglądu została
zablokowana przez politykę dostępu narzędzia przeglądarkowego.

## Czego ten etap nie robi

Nie poprawia rozpoznawania Robloxa/kebaba/dokumentów. Nie łączy jeszcze OCR
i wizji w model rozumiejący kontekst nazw, nie dodaje wyboru modeli ani GPU.
Nie usuwa przyczyny ewentualnego zawieszenia usługi analizy w Rust/Python.
Eksport zapisuje kopie; nie przenosi ani nie zmienia nazw oryginałów i nie usuwa
dokumentów. Nowy backend uruchamia migrację V8 przy starcie. Zainstalowane `.exe`
otrzyma tę funkcję dopiero po aktualizacji do wydania 0.2.3.
Te ograniczenia są nadal istotne przy ocenie produktu, niezależnie od wyglądu.
