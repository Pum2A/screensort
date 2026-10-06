# Galeria ScreenSort: co dodaliśmy i dlaczego

Stan: 2026-10-06. Ten opis dotyczy działającego kodu, nie przyszłego OCR.

## Co widać w aplikacji

Katalog to teraz siatka małych kafelków. Kafelek pokazuje obraz, nazwę, format
i rozmiar. Kliknięcie otwiera większy podgląd oraz szczegóły. SHA-256, ID
i dokładne daty UTC są w rozwijanej sekcji „Dane techniczne”. Niczego nie
usuwamy z opisu pliku; chowamy mniej przydatne informacje, żeby nie zajmowały
całego ekranu.

Jasne tło, zielony akcent, zaokrąglone narożniki i przyciski z dolnym cieniem
to kierunek wizualny inspirowany połączeniem przyjaznego Duolingo i spokojnego
interfejsu produktowego Revolut. Nie kopiujemy ich ekranów ani oznaczeń.

Na dużym ekranie szczegóły są obok siatki, na małym — nad nią. Escape zamyka
szczegóły i przywraca fokus wybranemu kafelkowi. To wybór jednego obrazu do
podglądu, jeszcze nie wielokrotny wybór do kopiowania.

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
   → ImageDetailsPanel pokazuje opis
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
| `frontend/src/catalog/CatalogGallery.tsx` | Siatka i `selectedId` | Logika wyboru obrazu należy do galerii |
| `frontend/src/catalog/CatalogImage.tsx` | Ładowanie obrazu, błąd, próba ponownego odczytu dużego podglądu | Ten sam sposób obsługi obrazów w kafelku i panelu |
| `frontend/src/catalog/ImageDetailsPanel.tsx` | Większy obraz, metadane, dane techniczne | Szczegóły nie rozpychają wszystkich kafelków |
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
w panelu. Przechowujemy tylko ID, a `find` wybiera obiekt z bieżącej strony.
Tu jest maksymalnie 50 elementów, więc nie potrzeba dodatkowej HashMap.
To zalecany wzorzec unikania powielonego stanu w
[dokumentacji Reacta](https://react.dev/learn/choosing-the-state-structure#avoid-duplication-in-state).

`key` galerii zmienia się wraz z folderem, stroną albo odświeżeniem. Nowa
galeria nie zachowuje starego zaznaczenia. `key` komponentu obrazu to jego
adres: inny obraz zaczyna z nowym stanem ładowania, zamiast odziedziczyć błąd
poprzedniego. React opisuje to w
[resetowaniu stanu przez key](https://react.dev/learn/preserving-and-resetting-state#resetting-state-with-a-key).

## Limity i pamięć

| Ustawienie | Obecna wartość | Cel |
|---|---|---|
| Strona katalogu | Do 50 opisów | Ograniczamy JSON i liczbę kafelków |
| Miniatura | Maksymalny bok 320 px | Nie wysyłamy megabajtowego oryginału do małego kafelka |
| Większy podgląd | Maksymalny bok 1024 px | Większy obraz dopiero po wyborze |
| Mały obraz | Bez powiększania | Nie rozciągamy sztucznie kilku pikseli |
| Źródło podglądu | Do 50 MiB, 40 mln pikseli, 20 000 px na bok | Odrzucamy nadmierne rozmiary przed dekodowaniem |
| Dekodowanie | Maksymalnie 2 równocześnie | Żądania 50 kafelków nie uruchamiają 50 dekoderów |
| Oczekiwanie na dekoder | Do 2 sekund, potem HTTP 503 | Nie czekamy bez końca; w panelu można spróbować ponownie |
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

Metadane panelu pochodzą z ostatniego skanu, podgląd z bieżącego pliku na
dysku. Sama galeria nie synchronizuje bazy. EXIF orientation zdjęć JPEG nie
jest jeszcze obsługiwane — podgląd może wymagać obrotu w następnym etapie.

Rozmiary binarne wyświetlamy jako KiB/MiB/GiB, bo dzielimy przez 1024, nie 1000.
W bazie nadal zapisujemy dokładną liczbę bajtów.

## Jak sprawdzić bez zmieniania swoich zdjęć

1. Uruchom backend z profilem `local` i frontend / Tauri.
2. Otwórz już zeskanowany folder. Katalog powinien pokazać miniatury bez skanu.
3. Kliknij kafelek: większy obraz i dane tego samego ID powinny być w panelu.
4. Rozwiń „Dane techniczne”; sprawdź ID, SHA-256 i daty.
5. Zamknij krzyżykiem albo Escape. Zmień rozmiar okna i stronę katalogu.

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

Nie dodaliśmy migracji, OCR, tagów, propozycji nazw ani zapisu kopii.
Najpierw omawiamy te zmiany; następny etap produktu wybieramy wspólnie.
