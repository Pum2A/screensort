# v1+: czego jeszcze nie ma (kandydaci na issues)

Stan po wydaniu 0.3.0: PR #30–#33 (fundament, pasek boczny, Biblioteka + Duplikaty, Przegląd hurtowy) oraz audyt #13, #16, #22, #23, #24.
Każda sekcja to jedno issue: tytuł, co brakuje, dlaczego nie weszło i co trzeba zrobić.
Rozmiar: **S** = kilka godzin, **M** = dzień lub dwa, **L** = większa zmiana w backendzie lub modelach.

## Ekrany jeszcze w starym układzie

### 02 · Szczegóły zdjęcia jako pełny ekran (M)
- Teraz kliknięcie kafelka otwiera stare okno podglądu z drzewkiem.
- Figma: pełny ekran z paskiem miniatur na dole, strzałkami „8 z 94”, ramkami tekstu z OCR na obrazie i historią pliku.
- Brakuje: układu, nawigacji strzałkami i ramek OCR. Ramki wymagają pozycji tekstu, a OCR ich dziś nie zapisuje (zob. „Ramki tekstu z OCR”).

### 04 · Skanowanie na żywo (M)
- Postęp skanu to wciąż stare komponenty `ScanProgress` / `ScanSummary`.
- Figma: siatka zapełniająca się na żywo, sekcje „Gotowe teraz / W analizie / W kolejce”.

### 05 · Pierwszy start (S)
- Bez wybranego folderu jest tylko pusty stan i stare pole ścieżki.

### 07 · Kopia uporządkowana (M)
- Eksport wciąż działa przez stary podgląd planu w „Do decyzji”.
- Figma: osobny widok „Kopie” w pasku bocznym, drzewo folderów z miniaturami, przełączniki (pomiń duplikaty, data w nazwie, podfoldery), lista poprzednich kopii.

### 08 · Silnik AI (M)
- Jest tylko karta silnika w pasku bocznym.
- Brakuje pełnego ekranu i zużycia RAM: powłoka Tauri nie podaje pamięci procesu analizy.

### 09 · Stany: menu kontekstowe, cofnij, wyszukiwanie (M)
- Nie ma menu pod prawym przyciskiem, toastu „Cofnij” ani wyszukiwania z wynikami z OCR.

## Biblioteka (01)

### Procent pewności na kafelkach (L)
- Figma pokazuje „92%”. Modele (RapidOCR, Florence-2) nie zwracają jednej pewności propozycji, więc kafelki mają znaczek „AI” albo „!”.
- Do zrobienia: zdefiniować pewność, np. ze średniej pewności OCR i reguł kategorii, zapisać ją w `analysis_batch_item` (migracja) i pokazać.

### Wyszukiwanie po tekście z obrazu (M)
- Szukajka filtruje tylko nazwy plików.
- Do zrobienia: endpoint, który szuka w zapisanym tekście OCR. Zgodnie z zasadą prywatności nie wolno go logować. PR #26 ma backendowe szukanie po nazwie, od którego można zacząć.

### Własne kategorie: „Rozmowy”, „Nowa kategoria”, „Edytuj” (L)
- Kategorie to stały enum `ImageCategory` (5 pozycji), z którego korzysta eksport przy nazwach folderów.
- Do zrobienia: tabela kategorii (migracja), reguły w `CategorySuggester`, mapowanie folderów w eksporcie i edytor w UI.

### Hurtowa zmiana nazw według wzoru (M)
- „Zmień nazwy” w pasku akcji, np. `roblox_*` → `roblox_01.png`, `roblox_02.png`.
- Do zrobienia: podgląd przed i po, sprawdzenie kolizji nazw, zapis przez istniejący endpoint nazw.

### Kopia tylko zaznaczonych plików (M)
- Eksport kopiuje wszystkie zatwierdzone pliki z ostatniej analizy.
- Do zrobienia: przekazać do eksportu listę ID.

### Widok listy i gęstość siatki S/M/L (S)
- Przełącznik w Figmie obok sortowania.

### Cofnij ostatnią decyzję (M)
- Toast „Cofnij” po zatwierdzeniu lub pominięciu.
- Do zrobienia: zapamiętać poprzednią decyzję i rewizję i zapisać ją z powrotem. Nie ma dziś akcji „usuń decyzję”, więc potrzebny jest endpoint.

### Foldery powyżej 1000 obrazów (M)
- `/api/library` zwraca błąd powyżej 1000 plików (limit z `CatalogReader.read`). Analiza nadal działa.
- Do zrobienia: odczyt strumieniowy albo stronicowany po stronie serwera, z licznikami i grupami liczonymi w SQL.

### Kilka folderów naraz w pasku bocznym (L)
- Aplikacja pracuje na jednym folderze. Kafelek folderu pokazuje zaślepki zamiast miniatur.

## Duplikaty (06)

### Wymiary zdjęcia i wybór „lepszej jakości” (M)
- Baza nie zna szerokości ani wysokości obrazu. Skan musiałby je zapisywać (migracja w `file_record`).

### Prawie identyczne obrazy (L)
- Dziś grupujemy tylko pliki identyczne bajt w bajt (SHA-256).
- Figma pokazuje „podobieństwo 99,2%”. To wymaga hasha percepcyjnego (pHash/dHash) liczonego przy skanie.

### „To nie duplikat” i cofnięcie pominięcia (S/M)
- Przy identycznym SHA-256 „to nie duplikat” nie ma sensu. Ma go dopiero przy prawie identycznych obrazach.
- Cofnięcie pominięcia wymaga tej samej akcji „usuń decyzję” co toast „Cofnij”.

### Przełącznik „Pomiń duplikaty” w eksporcie (S)
- Dziś trzeba ręcznie zostawić jedną kopię. Eksport mógłby sam pomijać kolejne pliki o tym samym SHA-256.

## Przegląd hurtowy (03)

### Podobne grupy (L)
- Zakładka „Podobne grupy” (np. „Roblox · 3”) z jednym zatwierdzeniem na grupę.
- Wymaga grupowania po podobieństwie: opis wizualny, tekst OCR albo hash percepcyjny.

### Wybór kategorii z karty w formularzu po prawej (S)
- Kategoria wybrana na karcie nie przechodzi do pola kategorii w edytorze, który ma własny formularz.

### Wzór nazwy i Cofnij
- Te same zadania co w Bibliotece.

## Porządki po redesignie

### Nieaktualne dokumenty (S)
- `docs/galeria-i-podglady.md`, `docs/review-explorer-ui.md`, `docs/review-organization-decisions.md`, `docs/device-handoff.md` i `docs/decyzje-techniczne.md` opisują usunięte `CatalogGallery`, `CatalogDetailsPanel`, `ReviewQueue`.

### Endpoint `/api/organization/batches/{id}/items` (S)
- UI już go nie używa, bo przegląd czyta `/api/library`. Do decyzji: usunąć albo zostawić dla API.

### `/api/library` w formacie błędów `ApiError` (S)
- #22 wprowadził jeden format błędu dla całego API. `LibraryController` (z #32) powstał wcześniej i nadal zwraca zwykły tekst.
- Front pokazuje wtedy ogólny komunikat, więc działa, ale kontrakt jest niespójny.
- Do zrobienia: przenieść jego obsługę błędów do wspólnego handlera.

### PR-y zastąpione przez v1+ (S)
- Otwarte i nie scalone: #25 (podział App + TanStack dla strony katalogu), #26 (szukanie po nazwie), #27 (tokeny Tailwind), #28 (stary wygląd z Figmy).
- Wszystkie cztery zmieniają stary frontend (`App`, `CatalogGallery`, kolory), który v1+ zastąpił. Scalenie cofnęłoby nowy wygląd albo skończyło się konfliktami w każdym pliku.
- Do decyzji: zamknąć. Z #26 warto przenieść backendowe szukanie po nazwie i podpiąć je pod szukajkę biblioteki.
