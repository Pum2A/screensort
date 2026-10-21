# Przegląd nazw i kategorii — co zmieniliśmy w kodzie

Etap: 2026-10-19, desktop 0.2.1. Zmiany są na gałęzi
`codex/on-demand-ai-and-batch-review`, a nie automatycznie na `main`.

## Efekt, który możesz sprawdzić

Po „Analizuj wszystko” wybierasz obraz z listy. Po prawej masz podgląd,
edytowalną nazwę i kategorię. „Zatwierdź” zapisuje decyzję w PostgreSQL.
„Pomiń” wyłącza obraz z zatwierdzonych propozycji, ale pozwala później wrócić.
Zapis przetrwa restart aplikacji. Nie powstają jeszcze foldery wyjściowe.
Żaden z tych przycisków nie zmienia nazwy ani zawartości oryginału.

To nie nowe trenowanie Florence ani OCR. Podpowiedzi kategorii nadal pochodzą
z wcześniejszych reguł `CategorySuggester`. Kategoria „Do sprawdzenia” jest
sygnałem niepewności, nie kategorią do zapisania. Możesz wybrać „Pozostałe”.

## Jeden konkretny przepływ danych

```text
Zapisany wynik analizy folderu
          │ GET /api/organization/batches/{batchId}/items?page=0
          ▼
OrganizationPanel → ReviewQueue → ReviewEditor
                                  │ własna nazwa + kategoria
                                  │ PUT .../items/{fileId}/review
                                  ▼
Rust: sprawdzenie dozwolonej trasy → Java: ReviewController
                                             │
                                             ▼
                                      ReviewService.save
                                blokada rekordu + porównanie wersji
                                             │
                         jedna transakcja PostgreSQL
                          ┌──────────────────┴──────────────────┐
                          ▼                                     ▼
                   NamingService.approve                 ReviewStore.save
                    tabela file_naming             tabela file_organization_review
                          └──────────────────┬──────────────────┘
                                             ▼
                             ReviewItem → JSON → aktualizacja UI
```

Python nie bierze udziału w zatwierdzaniu. Nie ma potrzeby ponownego
wywoływania modelu tylko dlatego, że poprawiasz nazwę.

## Gdzie zacząć code review

| Kod | Co rzeczywiście się zmieniło |
| --- | --- |
| `src/main/resources/db/migration/V7__create_organization_reviews.sql` | Nowa tabela decyzji. Starsze migracje i dane zostają. |
| `organization/review/ReviewItem.java` | Dane jednego wiersza kolejki, w tym osobno sugestia i decyzja człowieka. |
| `organization/review/ReviewService.java` | Kolejność kontroli, transakcja, zatwierdzenie lub pominięcie. |
| `organization/review/ReviewStore.java` | Odczyt SQL i zapis decyzji, bez operacji na zdjęciach. |
| `organization/review/ReviewController.java` | GET formularza i PUT decyzji; tłumaczenie błędów na HTTP. |
| `organization/BatchStore.java`, `BatchController.java` | Strona wyników korzysta ze wspólnego odczytu, zawiera teraz również decyzję. |
| `frontend/src/organization/review/state.ts` | Zbudowanie formularza, wykrywanie zmian i utworzenie żądania. |
| `frontend/src/organization/review/api.ts` | GET/PUT i czytelne komunikaty zamiast surowego błędu serwera. |
| `ReviewEditor.tsx` | Pola, walidacja, zapis, konflikt i świadome odrzucenie edycji. |
| `ReviewQueue.tsx` | Wybrany obraz, lista i potwierdzenie porzucenia edycji. |
| `OrganizationPanel.tsx` | Połączenie kolejki z istniejącą analizą i odświeżaniem postępu. |
| `frontend/src-tauri/src/desktop/mod.rs` | Dopuszczenie dokładnie GET/PUT nowej trasy w natywnym bridge. |
| `frontend/src-tauri/src/desktop/smoke.rs` | Test prawdziwego API: pominięcie, zatwierdzenie, konflikt i restart. |
| `frontend/vite.config.ts` | Dev server nie obserwuje `src-tauri`, czyli tysięcy plików runtime/build. |

Ścieżki `organization/...` w tabeli zaczynają się w
`src/main/java/pl/pum/screensort/`. Komponenty Reacta są w
`frontend/src/organization/review/`.

## Java: co oznacza każda część nowej metody

Główna metoda ma podpis:

```java
@Transactional
public ReviewItem save(UUID batchId, long fileId, Request request)
```

`batchId` wskazuje uruchomienie analizy całego folderu. `fileId` wskazuje
konkretny obraz w katalogu. To różne identyfikatory. `request` to treść
formularza z Reacta. Wynikiem jest aktualny `ReviewItem`, a nie encja JPA.

`@Transactional` obejmuje zarówno zapis nazwy przez JPA, jak i zapis decyzji
przez `JdbcTemplate`. Używają tej samej bazy i menedżera transakcji.
Jeśli drugi zapis rzuci wyjątek, pierwszy też ma zostać wycofany.
Sprawdzamy to testem, który celowo powoduje błąd SQL już po zapisaniu nazwy.

Kolejne instrukcje w `ReviewService.save` mają konkretne role:

| Instrukcja / fragment | Po ludzku: po co tu jest |
| --- | --- |
| `validate(fileId, request)` | Zanim zajmiemy bazę, odrzucamy brak ID, zły SHA, brak wersji, nieznaną kategorię lub decyzję. `Long`, zamiast `long`, pozwala odróżnić brak pola JSON od liczby 0. |
| `files.findForNamingById(fileId)` | Znajdujemy istniejący plik i blokujemy jego rekord na czas zapisu. Dwa okna nie mogą jednocześnie wykonać pierwszego zatwierdzenia tego samego obrazu. To blokada wiersza bazy, nie pliku Windows. |
| `reviews.item(batchId, fileId)` | Sprawdzamy, czy ten obraz naprawdę należy do wskazanego zadania. Samo poprawne ID obrazu nie wystarcza. |
| sprawdzenie `COMPLETED` i `analysisId` | Nie zatwierdzamy wyniku, który jeszcze nie powstał. |
| `item.analysisMatchesCatalog()` | SHA zapisanej analizy ma pasować do obecnego katalogu. Stary opis zmienionego obrazu nie powinien dostać zielonego zatwierdzenia. |
| porównanie `analysisId` i `sourceSha256` | Formularz ma dotyczyć analizy i treści, które użytkownik zobaczył, a nie innego wyniku otrzymanego w międzyczasie. |
| porównanie `reviewRevision` i `namingRevision` | Ktoś mógł zmienić kategorię w kolejce albo nazwę w istniejącym panelu pojedynczego obrazu. Kontrolujemy obie możliwości. |
| `Long approvedNamingRevision = null` | Pominięcie nie zatwierdza nazwy, więc na razie nie wiążemy decyzji z żadną wersją nazwy. |
| `if ("APPROVED".equals(request.decision()))` | Tylko zatwierdzenie uruchamia zapis nazwy. Nie robimy tego przy „Pomiń”. |
| `naming.approve(...)` | Ponownie wykorzystujemy istniejące reguły nazw i zapis `file_naming`. Nie tworzymy drugiej, rozjeżdżającej się implementacji. |
| `.revision()` z wyniku zatwierdzenia | Zapamiętujemy dokładnie tę wersję nazwy, którą zatwierdzono razem z kategorią. |
| `reviews.save(...)` | Zapis decyzji, kategorii, hasha i powiązania z nazwą. |
| końcowe `reviews.item(...)` | Odpowiadamy stanem faktycznie zapisanym w bazie. React nie musi zgadywać nowego numeru wersji. |

Brak dodatkowego `try/catch` w tej metodzie jest zamierzony: nie połykamy błędu
i nie zwracamy pozornego sukcesu. Kontroler tłumaczy znane wyjątki na czytelny
kod odpowiedzi. Błąd zapisu przerywa transakcję.

## Co wysyła React — przykład, nie dane prywatne

```json
{
  "analysisId": 8,
  "sourceSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "namingRevision": -1,
  "reviewRevision": -1,
  "decision": "APPROVED",
  "name": "kubek_na_biurku.png",
  "category": "PHOTOGRAPHS"
}
```

`-1` oznacza „jeszcze nie ma zapisu”, nie numer obrazu. Pierwszy zapis ma
wersję 0, kolejna zmiana zwiększa numer. Przykład: dwa okna czytają `-1`.
Pierwsze zapisuje wersję 0. Drugie nadal przysyła `-1`, więc dostaje
`409 REVIEW_CHANGED`, zamiast nadpisania pierwszej decyzji.

Dla `SKIPPED` pola `name` i `category` są `null`. Pominięcie nie usuwa
ewentualnej wcześniejszej nazwy z `file_naming`; tylko decyzja organizacji
zmienia się na „pominięte”. Żądanie nie zawiera dowolnej ścieżki wyjściowej
ani bajtów zdjęcia.

## SQL: dlaczego nowa tabela, zamiast dopisania wszystkiego do wyniku AI

`file_organization_review.file_record_id` jest jednocześnie kluczem głównym
i obcym. Oznacza to jedną bieżącą decyzję organizacji na obraz. Zatwierdzenie
nie mnoży się przy kolejnych analizach tego samego pliku.

`analysis_id` i SHA opisują pochodzenie decyzji. `category` jest kategorią
wybraną przez człowieka, a nie nadpisaną predykcją AI. `reviewed_at` mówi,
kiedy zapisano decyzję. `revision` chroni przed nieaktualnym formularzem.
`naming_revision` łączy kategorię z konkretną wersją zatwierdzonej nazwy.

Instrukcja `INSERT ... ON CONFLICT (file_record_id) DO UPDATE` oznacza:
„dodaj pierwszą decyzję; jeśli już istnieje, zaktualizuj ten sam wiersz”.
Sama ta instrukcja nie chroniłaby przed starym formularzem — dlatego wcześniej
jest blokada rodzica i kontrola wersji w `ReviewService`.

Odczyt `ReviewStore.SELECT` łączy przez `JOIN` informacje zadania, pliku,
analizy, nazwy i decyzji. Nie pobiera zawartości zdjęć. Lista ma 50 wierszy
i jeden dodatkowy do ustalenia `hasNext`; nie wykonuje osobnego zapytania
dla każdego obrazu. Miniatura i większy podgląd korzystają z istniejącego
API po ID, limitów dekodowania i cache.

Gdy nazwa zostanie później zmieniona w innym panelu, stara decyzja organizacji
nie znika, ale przestaje być uznawana za aktualne zatwierdzenie. Potrzebuje
ponownego sprawdzenia. Zmiana hasha w katalogu również ją unieważnia.

Ważne ograniczenie: porównujemy z zapisanym katalogiem. Jeśli plik na dysku
zmienił się bez kolejnego skanu, samo zatwierdzenie nie wykryje tego zdarzenia.
Przyszły eksport musi odczytać i potwierdzić rzeczywiste źródło ponownie.

## React: co robią nowe funkcje i dlaczego są rozdzielone

`draftFor(item)` tworzy formularz z pobranego wiersza. Najpierw bierze własną,
aktualną nazwę, potem podpowiedź AI, a na końcu nazwę oryginału. Nie zmienia
samego obiektu pobranego z API.

`useState(() => draftFor(item))` w `ReviewEditor` wykonuje tę inicjalizację
przy otwarciu obrazu, nie przy każdym odświeżeniu postępu. `key={fileId}`
w `ReviewQueue` daje nowy formularz po świadomej zmianie obrazu.

`draftChanged` porównuje nazwę i kategorię z początkiem edycji.
`serverChanged` porównuje wersje oraz analizę formularza z nowym stanem API.
To dwie różne sprawy: własna edycja nie jest tym samym, co zewnętrzny zapis.
Odświeżanie nie podnosi po cichu numeru wersji użytkownika.

`reviewWrite` buduje JSON tylko z potrzebnych pól. `saveReview` wysyła PUT,
a `readReview` wykonuje GET dopiero przy świadomym ponownym wczytaniu.
`AbortController` ignoruje spóźniony wynik po zamknięciu formularza.
`pending.current` blokuje drugi zapis, zanim React zdąży odmalować przycisk.
Przerwanie oczekiwania w UI nie jest obietnicą wycofania zapisu w Javie.

Po sukcesie `OrganizationPanel.reviewed` przerywa wcześniejsze odczyty
postępu, podmienia zapisany wiersz i uruchamia nowe odświeżenie. Starszy GET
nie powinien zastąpić nowszej decyzji. Pauza/wznowienie analizy nie usuwa
formularza. Zmiana strony i start nowej analizy są blokowane w trakcie edycji.
Przy zmianie wybranego obrazu pojawia się potwierdzenie porzucenia zmian.

Układ listy i formularza używa Tailwinda i ikon Lucide. Na wąskim ekranie
przechodzi w jedną kolumnę. Nie dodaliśmy biblioteki formularzy, globalnego
store ani kolejnego zestawu komponentów tylko do dwóch pól.

## Rust i Python w tym etapie

Rust nie zapisuje kategorii w PostgreSQL samodzielnie. Native bridge dopuszcza
wyłącznie GET/PUT konkretnej trasy z ID zadania i dodatnim ID obrazu.
Nie dopuszcza przez to dowolnych żądań HTTP, eksportu ani zmiany ścieżek.
Porty i token sesji nadal pozostają po stronie natywnej.

W Pythonie nie było zmian. Nowy Core korzysta z tego samego opcjonalnego pakietu
CPU, więc ta funkcja nie wymaga ponownego pobierania modeli.

## Jak sprawdzić to samemu, bez pisania testu

Z katalogu `frontend` uruchom `npm run review:preview`, następnie otwórz:
`http://127.0.0.1:5183/tests/fixtures/review-preview.html`.
To osobny ekran testowy: fikcyjne dane, syntetyczne miniatury i brak połączenia
z prawdziwą bazą. Nie jest dodany do produkcyjnego wejścia aplikacji.

Zmień nazwę. Nawigacja strony zostanie zablokowana do zapisu lub odrzucenia
edycji. Kliknij drugi obraz — zobaczysz potwierdzenie porzucenia zmian.
Zatwierdź obraz z wybraną kategorią, potem go pomiń i wróć do zatwierdzenia.
Przycisk „Symuluj zmianę w drugim oknie” pozwala sprawdzić konflikt: wpisana
własna nazwa ma pozostać w polu, a nie zniknąć. Wczytanie aktualnego formularza
jest osobnym przyciskiem. Drugi obraz ma niepewną kategorię, trzeci błąd.
Odświeżenie tej karty resetuje fikcyjne dane — to nie test trwałości bazy.

Prawdziwy zapis sprawdzisz w Core 0.2.1: otwórz zeskanowany folder,
uruchom AI i „Analizuj wszystko”, zatwierdź nazwę/kategorię, zamknij aplikację
i ponownie otwórz ten folder. Oryginał na dysku ma zachować starą nazwę.
Nie usuwaj bazy ani katalogu danych w celu aktualizacji.

## Weryfikacja i dalsza granica etapu

Testy `ReviewServiceTest` sprawdzają błędne żądania i warunki zapisu.
`ReviewControllerTest` sprawdza JSON, GET/PUT, brak cache i kody błędów.
Osiem `ReviewJpaTest` używa rzeczywistego, odizolowanego PostgreSQL-a:
odczyt po zapisie, pominięcie, nieaktualny formularz, zmieniony hash,
zmiana nazwy z innego panelu, niebezpieczna nazwa, rollback obu zapisów
i dwa jednoczesne pierwsze zatwierdzenia. Baza właściciela jest poza testem.

Frontend ma testy budowania formularza, wykrywania konfliktów i żądań HTTP.
Test ręczny obejmuje zapis, konflikt z zachowaniem edycji i układ 390 px.
Natywny smoke dodatkowo sprawdza rzeczywisty backend, analizę, pominięcie,
zatwierdzenie, konflikt i odczyt po restarcie z niezmienionym źródłem.
Pełne uruchomienie tego smoke dla 0.2.1 przeszło 2026-10-19: Core bez
dołączonego AI, instalacja lokalnej zaufanej paczki ZIP na nowych danych,
rzeczywisty OCR i Florence, analiza folderu, zapis decyzji, konflikt,
odczyt po restarcie oraz niezmienione bajty i czas modyfikacji źródła.
To nie był test instalowania w profilu właściciela ani pomiar wydajności modeli.

Najbliższy osobny etap to **podgląd planu eksportu**: pokażemy wynikowe
`kategoria/nazwa` i kolizje, ale jeszcze niczego nie skopiujemy. Dopiero po
zatwierdzeniu takiego planu dojdzie copy-first, bez nadpisywania, z ponowną
weryfikacją SHA źródła i kopii. Nie ma tego w obecnym przycisku „Zatwierdź”.

Dokumentacja użyta do sprawdzenia decyzji implementacyjnych:
[wspólne transakcje JPA/JDBC w Spring](https://docs.spring.io/spring-framework/reference/data-access/orm/jpa.html),
[zachowywanie stanu formularzy w React](https://react.dev/learn/preserving-and-resetting-state),
[watcher Vite](https://vite.dev/config/server-options.html#server-watch).
