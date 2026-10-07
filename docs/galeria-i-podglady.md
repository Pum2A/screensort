# Galeria ScreenSort: co dodaliśmy i dlaczego

Stan: 2026-10-07. Ten opis dotyczy działającego kodu, nie przyszłego OCR.

## Co widać w aplikacji

Katalog jest siatką okrągłych miniatur z nazwą, formatem i rozmiarem. Zachowujemy
wybrany styl retro i gałęzie, ale zgodnie z kolejną prośbą właściciela kliknięcie
otwiera podgląd na środku ekranu. Galeria zostaje pod przyciemnionym i lekko
rozmytym tłem. Lewa część okna pokazuje większy obraz, a połączone liniami
gałęzie opisują „Plik”, „Lokalizację”, „Historię” i „Techniczne”. Na wąskim
ekranie układają się pionowo i przewijają wewnątrz okna.

Kółko przycina tylko miniaturę. Duży podgląd używa `object-fit: contain`, więc
nie ucina brzegów zdjęcia. SHA-256, ID, dokładny rozmiar i daty UTC nadal są
dostępne w gałęzi „Techniczne”. Żadne pole DTO nie zostało usunięte.

Krzyżyk w prawym górnym rogu, Escape lub kliknięcie tła zamykają podgląd.
Kliknięcie danych albo przeciągnięcie z obrazu na tło nie zamyka go przypadkowo.
Galeria jest nieaktywna podczas podglądu; nie można wtedy uruchomić skanu.
Fokus zaczyna na X, Tab / Shift+Tab poruszają się po oknie, a zamknięcie oddaje
fokus miniaturze bez dodatkowego przewijania. Nagłówek z X nie przewija się
razem z danymi. Każdą gałąź można osobno zwinąć. To nadal pojedynczy podgląd,
nie wybór plików do kopii.

Wygląd jest inspirowany eksploratorem retro PC: kremowe tło, niebieski akcent,
cienkie linie i lokalny font VT323. Font wraz z licencją jest w repozytorium,
nie jest pobierany z CDN podczas działania. Licencja `frontend/public/VT323-OFL.txt`
trafia także do `dist`. Źródło: [oficjalne Google Fonts](https://github.com/google/fonts/tree/main/ofl/vt323).
Krótka animacja pojawienia się nie zmienia geometrii; preferencja ograniczenia
ruchu wyłącza animacje. API, skaner, baza i Rust nie zmieniły się w tym etapie.

## Trzy różne przepływy

```text
1. Otwórz folder / zmień stronę
   App.tsx → catalog/api.ts → GET /api/catalog/page
   → CatalogReader → PostgreSQL → CatalogPageDto → CatalogGallery
   Wynik: maksymalnie 50 opisów. Nie skanujemy folderu.

2. Widoczny kafelek
   CatalogImage → GET /api/catalog/files/{id}/thumbnail
   → CatalogImageController → CatalogImagePreview
     → CatalogImageSourceReader → rekord i folder z PostgreSQL
     → ponowna kontrola ścieżki i pliku
     → cache albo odczyt obrazu i wygenerowanie małego PNG
   Wynik: bajty miniatury, nie JSON i nie cały oryginał.

3. Kliknięcie kafelka
   CatalogGallery ustawia selectedId
   → znajduje opis w już pobranej stronie
   → ImageDetailsDialog otwiera natywny dialog na środku ekranu
   → ImageDetailsTree pokazuje obraz i połączone gałęzie wewnątrz dialogu
   → CatalogImage pobiera /preview dla tego ID
   Wynik: jeden większy podgląd, bez ponownego skanu i bez zapisu do bazy.
```

Przycisk „Skanuj folder” nadal wykonuje istniejący `ScanWorkflow`. Po sukcesie
wracamy do strony 0 i odświeżamy galerię. Podsumowanie skanu jest kompaktowe,
a powody odrzucenia można rozwinąć. Nie powielamy zaakceptowanych plików
w drugiej wielkiej liście nad katalogiem. API wyniku skanu nie zostało zmienione.

Wybranie folderu w Tauri teraz od razu próbuje odczytać jego zapisany katalog.
Nowy folder ma pustą galerię do pierwszego skanu. W przeglądarce odpowiednikiem
jest wpisanie ścieżki i przycisk „Otwórz katalog”. Ostatni folder w localStorage
nadal zapamiętujemy po udanym skanie, nie po samym otwarciu podglądu.

## Gdzie jest kod

| Plik | Co robi | Dlaczego osobno |
|---|---|---|
| `frontend/src/App.tsx` | Folder, skan, pobranie strony i nawigacja | Koordynuje ekran; nie rysuje wszystkich szczegółów kafelka |
| `frontend/src/catalog/types.ts` | Typy `CatalogFile` i `CatalogPage` | Jeden kształt danych zamiast kopii typów w komponentach |
| `frontend/src/catalog/api.ts` | Pobranie strony i adres podglądu po ID | Żądania katalogu w jednym miejscu |
| `frontend/src/catalog/CatalogGallery.tsx` | Siatka, `selectedId` i przycisk otwierający podgląd | Logika wyboru obrazu należy do galerii |
| `frontend/src/catalog/ImageDetailsDialog.tsx` | Centralne okno, X, Escape, tło i fokus | Oddziela obsługę okna od metadanych i SVG |
| `frontend/src/catalog/CatalogImage.tsx` | Ładowanie obrazu, błąd, próba ponownego odczytu dużego podglądu | Wspólna obsługa obrazów w kółku i podglądzie |
| `frontend/src/catalog/ImageDetailsTree.tsx` | Większy obraz i cztery rozwijane gałęzie | Zastępuje dawny boczny `ImageDetailsPanel` |
| `frontend/src/catalog/galleryLayout.ts` | Współrzędne linii i rozpoznanie kliknięcia poza oknem | Czyste obliczenia, możliwe do testowania bez DOM |
| `frontend/src/catalog/useTreeConnections.ts` | Pomiar węzłów i dopasowanie połączeń SVG | Geometria rysunku jest oddzielona od danych pliku |
| `frontend/src/catalog/format.ts` | Czytelny rozmiar, data i nazwa folderu | Wspólne formatowanie, nie zmieniamy danych zapisanych w bazie |
| `frontend/src/ui/Icon.tsx` | Małe ikony SVG | Bez nowego pakietu i bez zasobów z internetu |
| `catalog/api/CatalogImageController.java` | HTTP, PNG i statusy błędów | Nie wkładamy dekodowania do kontrolera |
| `catalog/preview/CatalogImageSourceReader.java` | Odczytuje rekord oraz relację z folderem | Krótka transakcja kończy się przed pracą z obrazem |
| `catalog/preview/CatalogImagePreview.java` | Sprawdza ścieżkę, ogranicza odczyt, przygotowuje PNG | Tutaj jest logika podglądów, niezależna od skanowania |
| `catalog/preview/PreviewCache.java` | Ograniczona pamięć gotowych PNG | Cache nie może rosnąć razem z każdym odwiedzonym folderem |
| `catalog/preview/ImagePreviewException.java` | Rozróżnia powody niedostępności | Kontroler zamienia je na HTTP, bez ujawniania ścieżek w błędzie |

Ścieżki Javy w tabeli zaczynają się od
`src/main/java/pl/pum/screensort/`. Testy znajdują się w analogicznych pakietach
pod `src/test/java`.

## Czemu w React tylko selectedId?

Opis pliku już jest w `catalogPage.files`. Gdybyśmy skopiowali cały opis do
drugiego stanu, po odświeżeniu moglibyśmy mieć nowy rekord w liście i stary
w drzewku. Przechowujemy tylko ID, a `find` wybiera obiekt z bieżącej strony.
Tu jest maksymalnie 50 elementów, więc nie potrzeba dodatkowej HashMap.
To zalecany wzorzec unikania powielonego stanu w
[dokumentacji Reacta](https://react.dev/learn/choosing-the-state-structure#avoid-duplication-in-state).

`key` galerii zmienia się wraz z folderem, stroną albo odświeżeniem. Nowa
galeria nie zachowuje starego zaznaczenia. `key` komponentu obrazu to jego
adres: inny obraz zaczyna z nowym stanem ładowania, zamiast odziedziczyć błąd
poprzedniego. React opisuje to w
[resetowaniu stanu przez key](https://react.dev/learn/preserving-and-resetting-state#resetting-state-with-a-key).

## Jak centralny podgląd otwiera się i zamyka

```text
kliknięcie kółka → selectedId → opis zdjęcia z bieżącej strony
→ montaż ImageDetailsDialog → useEffect → dialog.showModal()
→ centralne okno + ::backdrop + nieaktywna galeria

X / Escape / kliknięcie tła → selectedId = null → odmontowanie
→ cleanup: dialog.close(), odblokowanie przewijania, powrót fokusu

ResizeObserver → zmiana szerokości / rozwinięcie gałęzi
→ pomiar prostokątów podglądu i gałęzi
→ treeConnections → współrzędne SVG, bez kolejnego fetch i bez skanu
```

`<dialog>` to element przeglądarki, nie nowa biblioteka Reacta. `showModal()`
umieszcza go nad stroną i blokuje interakcję z tłem. Sam atrybut `open` tego
nie zapewnia. React przez `useEffect` otwiera okno po montażu, a cleanup zamyka
je po odmontowaniu. Ten sam układ setup/cleanup działa w developerskim
StrictMode. Wzorzec sprawdzono przez Context7 w
[oficjalnym przykładzie Reacta](https://react.dev/reference/react/useEffect#controlling-a-modal-dialog)
oraz [dokumentacji dialogu](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/dialog).

`onCancel` obsługuje Escape i czyści wybór w React — nie zostawiamy zamkniętego
dialogu przy nadal zaznaczonym ID. Kliknięcie tła rozpoznajemy po współrzędnych
poza prostokątem dialogu i wymagamy, aby naciśnięcie też zaczęło się na zewnątrz.
To chroni przed zamykaniem podczas przeciągania z obrazu. Krótka obsługa Tab
zawija pierwszy i ostatni widoczny przycisk/nagłówek; normalną nawigację pomiędzy
nimi zostawiamy przeglądarce. Nie dodajemy własnego `tabindex` do dialogu.

`useGalleryColumns` i `selectedRowEnd` zostały usunięte: nie szukamy już rzędu,
pod którym można wstawić szczegóły. Miniatury nie przesuwają się przy wyborze.
SVG łączy tylko podgląd z czterema gałęziami, nie próbuje prowadzić linii
do zasłoniętej miniatury. CSS centruje dialog; Java, baza i Rust nie uczestniczą
w pozycjonowaniu okna.

Hook to funkcja Reacta organizująca stan lub efekt komponentu. `useTreeConnections`
przechowuje jedynie układ, nie drugą kopię danych z bazy. `useLayoutEffect`
mierzy DOM; używamy go tylko do podglądu i czterech gałęzi,
nie do pobierania danych ani kosztownego dekodowania. `ResizeObserver` informuje
o zmianie wymiarów. Aktualizacje linii łączymy w jedną klatkę przez
`requestAnimationFrame`, a nie odpytywanie w pętli. Cleanup odłącza obserwatory
i anuluje oczekującą klatkę po odmontowaniu — również w developerskim StrictMode.
Zalecenia cyklu życia sprawdzono przez Context7 w oficjalnej
[dokumentacji Reacta](https://react.dev/reference/react/useLayoutEffect).

Gałęzie używają natywnych `<details>/<summary>`: przeglądarka zapewnia ich
otwieranie i obsługę klawiatury. Nie potrzebujemy biblioteki grafów, dodatkowych
stanów dla każdego pola ani frameworka animacji. SVG jest tylko dekoracją
z `aria-hidden`; treść pozostaje zwykłym czytelnym HTML.

## Limity i pamięć

Dialog istnieje tylko podczas podglądu jednego pliku. Duży obraz pobieramy
dopiero po kliknięciu, nie dla całej strony. Rozmycie tła ma 3 px i nie jest
animowane; bez obsługi rozmycia nadal działa przyciemnienie. Nie zmienialiśmy
algorytmu skanu ani limitów backendu i nie twierdzimy, że modal przyspiesza skan.

| Ustawienie | Obecna wartość | Cel |
|---|---|---|
| Strona katalogu | Do 50 opisów | Ograniczamy JSON i liczbę kafelków |
| Miniatura | Maksymalny bok 320 px | Nie wysyłamy megabajtowego oryginału do małego kafelka |
| Większy podgląd | Maksymalny bok 1024 px | Większy obraz dopiero po wyborze |
| Mały obraz | Bez powiększania | Nie rozciągamy sztucznie kilku pikseli |
| Źródło podglądu | Do 50 MiB, 40 mln pikseli, 20 000 px na bok | Odrzucamy nadmierne rozmiary przed dekodowaniem |
| Dekodowanie | Maksymalnie 2 równocześnie | Żądania 50 kafelków nie uruchamiają 50 dekoderów |
| Oczekiwanie na dekoder | Do 2 sekund, potem HTTP 503 | Nie czekamy bez końca; w podglądzie można spróbować ponownie |
| Cache | Do 128 wpisów i 16 MiB zakodowanych PNG | Usuwamy najdawniej używane wpisy, gdy brakuje miejsca |

To początkowe limity, nie dowód optymalnych wartości dla każdego komputera.
16 MiB dotyczy tylko przechowywanych PNG, nie całej pamięci JVM. Sam dekoder,
bufor wejściowy i kopie odpowiedzi również zajmują pamięć. PNG może wymagać
przejścia przez wiele skompresowanych danych mimo małego wyniku.

`ImageReader` pozwala poznać wymiary przed `read`, a parametr subsamplingu
ogranicza liczbę pikseli wynikowego odczytu. Potem dopasowujemy obraz do
docelowego boku i ponownie kodujemy do PNG. Zachowujemy proporcje i kanał alfa,
ale podgląd nie jest kopią oryginalnych bajtów. Zobacz
[ImageReader](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/ImageReader.html)
i [ImageReadParam](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/ImageReadParam.html).

`MemoryCacheImageInputStream` używa pamięci zamiast automatycznego pliku cache
na dysku. Czytamy oryginał przez kanał otwarty tylko do odczytu. Dodatkowy
limit strumienia chroni również przed plikiem, który urośnie po kontroli
rozmiaru. Gotowe miniatury nie są zapisywane ani do folderu źródłowego, ani
do tabeli PostgreSQL. Źródło:
[Java Image I/O](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/stream/MemoryCacheImageInputStream.html).

## Bezpieczeństwo i uczciwe ograniczenia

Klient podaje ID rekordu, nie dowolną ścieżkę pliku. Po odczycie bazy ponownie
walidujemy folder i sprawdzamy plik. Dozwolone są tylko bezpośrednie, zwykłe
pliki tego folderu — zgodnie z obecnym nierekurencyjnym skanerem. Odrzucamy
ścieżki absolutne, `..`, podfoldery, linki, katalogi i zapis strumieni NTFS
przez dwukropek. Kanał otwieramy z `NOFOLLOW_LINKS`. Zmianę metadanych podczas
odczytu zgłaszamy jako konflikt, bez zapisu.

Cache jest sprawdzany dopiero po ponownej kontroli ścieżki i metadanych.
Usunięcie pliku albo zastąpienie go katalogiem nie powoduje wyświetlenia
miniatury z cache. Klucz obejmuje ID, ścieżkę, wariant podglądu, aktualny
rozmiar, pełny czas modyfikacji, klucz pliku i zapisany SHA-256.

Nie jest to dowód identyczności bajtów: podmiana z zachowaniem metadanych może
nie zostać wykryta. Kontrole ścieżek również nie są atomową blokadą przeciwko
złośliwemu procesowi równocześnie podmieniającemu foldery nadrzędne. Przed
publiczną dystrybucją potrzebujemy dalszego utwardzenia lokalnego API
i dostępu do plików. Żaden podgląd ani trafienie w cache nie uprawnia do
kopiowania bez świeżej weryfikacji SHA-256.

Obraz wysyłamy jako nowo zakodowane PNG z `nosniff`; nie udostępniamy surowych
plików ani SVG. `Cache-Control: no-store` wyłącza przechowywanie odpowiedzi
przez klienta: kolejne żądanie nadal przejdzie kontrolę backendu. Prywatny
cache backendu ogranicza ponowne dekodowanie. Backend domyślnie nasłuchuje
na `127.0.0.1`, nie we wszystkich interfejsach sieciowych. To nie zastępuje
autoryzacji wobec innych lokalnych procesów.

Metadane drzewka pochodzą z ostatniego skanu, podgląd z bieżącego pliku na
dysku. Sama galeria nie synchronizuje bazy. EXIF orientation zdjęć JPEG nie
jest jeszcze obsługiwane — podgląd może wymagać obrotu w następnym etapie.

Rozmiary binarne wyświetlamy jako KiB/MiB/GiB, bo dzielimy przez 1024, nie 1000.
W bazie nadal zapisujemy dokładną liczbę bajtów.

## Jak sprawdzić bez zmieniania swoich zdjęć

1. Uruchom backend z profilem `local` i frontend / Tauri.
2. Otwórz już zeskanowany folder. Katalog powinien pokazać miniatury bez skanu.
3. Kliknij kółko: na środku ekranu pojawi się większy obraz i dane tego samego ID.
4. Rozwiń „Techniczne”; sprawdź ID, SHA-256 i daty. Zwiń dowolną inną gałąź.
5. Zamknij przez X, Escape lub tło. Sprawdź fokus miniatury i otwórz inne zdjęcie.
6. Użyj Tab / Shift+Tab. Zmień rozmiar okna; sprawdź brak poziomego przewijania,
   przewijanie danych wewnątrz podglądu i widoczność X.

Testy samego układu uruchom w katalogu `frontend` przez `npm.cmd run test`.
Obecne 6 testów sprawdza rozpoznanie kliknięcia wewnątrz / poza oknem,
brak niepotrzebnych linii, połączenia desktopowe i pionowe oraz zmianę geometrii.
Używają wbudowanego test runnera Node i istniejącego TypeScript, bez nowej
biblioteki. `tsconfig.tests.json` emituje pliki do ignorowanego `.test-output`;
nie włącza ich do aplikacji. Konfigurację modułów sprawdzono przez Context7
w [dokumentacji TypeScript](https://www.typescriptlang.org/docs/handbook/modules/reference.html).

`npm.cmd run lint` sprawdza `src`, `tests` i `vite.config.ts`, nie biblioteki
w `node_modules` ani wyniki budowania. To zawężenie sprawdzanych ścieżek,
nie wyłączenie reguł. Składnię zweryfikowano przez Context7 w
[dokumentacji Oxlint](https://oxc.rs/docs/guide/usage/linter/quickstart.html).

Automatyczne testy używają tymczasowych plików, nie modyfikują źródeł
użytkownika. Sprawdzają rzeczywiste PNG/JPEG, rozmiary wyjścia, brak zmiany
oryginału, fałszywe obrazy, niebezpieczne ścieżki, limity wejścia, usunięcie
pliku po trafieniu do cache, invalidację po zmianie metadanych i limity cache.
Test API z Testcontainers przechodzi od skanu przez zapis JPA do odczytu PNG.
Test linku symbolicznego jest pomijany, jeśli Windows nie zezwoli go utworzyć.

Weryfikacja 2026-10-06: `clean verify` — 68 testów, 66 zaliczonych, 2 pominięte
(ręczny benchmark i link symboliczny bez uprawnień). Build i lint Reacta przeszły.
Ręcznie sprawdzono dwa rzeczywiste obrazy z bazy, wybór i przełączenie podglądu,
krzyżyk i Escape, dane techniczne oraz szerokości 320, 360 i 1024 px.
Nie uruchamiano nowego skanu ani zapisu w roboczej bazie podczas kontroli UI.

Weryfikacja centralnego podglądu 2026-10-07: build, lint i 6/6 testów frontendu
poprawne. Ręcznie sprawdzono dwa rzeczywiste obrazy z istniejącego katalogu,
Enter, X, Escape i kliknięcie tła z przywróceniem fokusu, zawijanie Tab / Shift+Tab,
przeciągnięcie z obrazu na tło, gałęzie techniczne i blokadę przewijania galerii.
Sprawdzono szerokości 320/360/1024/1280 px i niskie okno 1280 × 500 px;
dialog mieści się w ekranie, dane przewijają się wewnątrz, X zostaje widoczny.
Nie stwierdzono poziomego przewijania ani błędów konsoli w sprawdzonym przepływie.
Nie uruchamiano skanu i nie zmieniano rekordów ani obrazów użytkownika.
Backend oraz natywny picker Tauri nie były modyfikowane; nie powtarzano w tym
etapie pełnego zestawu Maven ani testu okna Tauri.

Nie dodaliśmy migracji, OCR, tagów, propozycji nazw ani zapisu kopii.
Najpierw omawiamy te zmiany; następny etap produktu wybieramy wspólnie.
