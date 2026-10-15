# ScreenSort: aplikacja Windows i prawdziwa analiza

Stan prac: 2026-10-15. Właściciel wybrał od razu instalator `.exe`, bez wymagania
Dockera, JDK, Node ani Pythona od użytkownika. To lokalna wersja przedpremierowa,
nie podpisana ani jeszcze w pełni audytowana dystrybucja produkcyjna.

[Raport wykonanej weryfikacji i ograniczenia](weryfikacja-instalatora.md).

Wersja 0.1.0 została zastąpiona poprawką 0.1.1 błędu ścieżek Windows.
[Przyczyna i test rzeczywistego startu przez Tauri](naprawa-startu-windows.md).

## Co uruchamia się po kliknięciu

1. Windows uruchamia natywny program Tauri/Rust. React jest wbudowanym interfejsem,
   nie serwerem Vite. Okno nie uruchamia przeglądarki na publicznym adresie.
2. Rust znajduje dołączone runtime'y. Tworzy sesję w katalogu danych użytkownika
   i losuje tokeny. Uruchamia Pythona, który ładuje i rozgrzewa OCR oraz Florence.
3. Rust inicjalizuje własny klaster PostgreSQL tylko przy pierwszym starcie.
   Potem używa tej samej bazy. Nie rejestruje usługi Windows ani nie uruchamia Dockera.
4. Dołączona Java uruchamia Spring Boot z profilem `desktop`, wykonuje migracje
   Flyway i publikuje swój losowy port. Rust sprawdza gotowość z tokenem sesji.
5. React pokazuje katalog po gotowości Javy. Analiza czeka dodatkowo na modele.
   Dzięki temu wolniejszy start modeli nie musi blokować przeglądania katalogu.
6. Zamknięcie okna zatrzymuje własne procesy. PostgreSQL dostaje polecenie
   kontrolowanego zatrzymania. Windows Job Object jest dodatkowym zabezpieczeniem:
   po awarii rodzica nie zostawiamy jego procesów. Nie zabijamy cudzych procesów
   znalezionych po nazwie albo numerze portu.

Pierwszy start jest wolniejszy: powstaje baza i ładowane są modele. To nie jest
koszt ponoszony ponownie dla każdego zdjęcia. Po restarcie baza zostaje,
modele muszą ponownie załadować się do RAM.

## Od kliknięcia „Analizuj obraz” do wyniku

* React zna ID zaznaczonego pliku, np. `12`, i wybrane kroki OCR/wizja.
  Wysyła `POST /api/analysis/jobs`, nie ścieżkę do otwarcia przez Pythona.
* W aplikacji desktop komunikację przejmuje polecenie Rusta `backend_request`.
  Dopuszcza tylko wskazane endpointy. Adres backendu i jego token nie trafiają
  do JavaScriptu, localStorage ani URL podglądu.
* `AnalysisJobManager` w Javie tworzy bilet UUID i zleca pracę w tle.
  React dostaje od razu HTTP 202, potem odczytuje status tego samego biletu.
  To inny typ zadania niż skanowanie folderu.
* `AnalysisSourceReader` odczytuje lokalizację z katalogu, ponownie sprawdza
  folder, plik i linki. Czyta najwyżej 50 MiB. Sprawdza metadane przed i po
  odczycie oraz oblicza SHA-256 rzeczywiście odczytanych bajtów.
* `PythonAnalysisClient` przesyła te bajty przez chronione HTTP na loopback.
  Python nie dostaje dostępu do JPA ani ścieżki katalogu użytkownika.
* `AnalysisRuntime` ma jeden dedykowany worker i jedną rezerwację pracy.
  Nie tworzy drugiej kopii modelu dla kolejnego żądania. Zajętość daje 429.
  Timeout HTTP nie potrafi bezpiecznie przerwać trwającego natywnego modelu:
  rezerwacja zostaje zajęta do rzeczywistego końca pracy.
* `ImageAnalyzer` przygotowuje obraz raz, uruchamia zamówione kroki i zwraca
  JSON. OCR i wizja niezależnie mają `COMPLETED`, `FAILED` albo `SKIPPED`.
  Pusty tekst OCR nie jest błędem; opis modelu może być niepoprawny znaczeniowo.
* Java sprawdza kontrakt, zamówione kroki i zgodność hasha. Krótka transakcja
  zapisuje `file_analysis`. Nie trzymamy transakcji bazy podczas pracy modelu.
* Dopiero po zapisie zadanie dostaje `COMPLETED`. React pokazuje wynik oraz
  czas silników. Otwarcie szczegółów później odczytuje wynik z bazy.

## Gdzie są poszczególne odpowiedzialności

| Miejsce | Odpowiedzialność |
| --- | --- |
| `frontend/src-tauri/src/desktop/processes.rs` | Uruchomienie, gotowość i zatrzymanie własnych procesów |
| `frontend/src-tauri/src/desktop/mod.rs` | Natywny, ograniczony most API i status startu |
| `frontend/src/desktop/` | Ekran startu i transport zamiast bezpośredniego HTTP w desktop |
| `frontend/src/analysis/AnalysisPanel.tsx` | Przycisk, status i zapisane rzeczywiste wyniki |
| `src/main/java/pl/pum/screensort/analysis/source/` | Bezpieczny odczyt oryginału po ID |
| `analysis/client/`, `analysis/jobs/` w Javie | Klient HTTP i cykl życia zadania |
| `analysis/database/` w Javie | Encja, repozytorium i krótkie transakcje zapisu/odczytu |
| `analysis-service/.../api/analysis.py` | Autoryzacja, ograniczony upload, opcje i odpowiedź HTTP |
| `analysis-service/.../image/runtime.py` | Jedna załadowana para silników i kontrola zajętości |

`file_analysis.result_json` jest tekstowym, wcześniej zwalidowanym snapshotem
kontraktu. Nie potrzebujemy teraz SQL-owego wyszukiwania we wnętrzu JSON, więc
nie dokładamy niestandardowego mapowania JSONB. Zapisujemy ostatni wynik dla
pary plik + opcje OCR/wizja. To nie nieskończona historia ani automatyczny cache
wyników modeli. Wersje silników/modelu są w zapisanym wyniku.

`matchesCatalog=false` oznacza, że hash analizy różni się od ostatniego skanu
katalogu. Wynik opisuje odczytane bajty; trzeba odświeżyć skan przed przyszłym
organizowaniem. Zgodne metadane skanu nadal nie są dowodem niezmienności bajtów.

## Dane, prywatność i ograniczenia

* Runtime jest w instalacji, a baza i logi w lokalnym katalogu danych Tauri:
  `%LOCALAPPDATA%\pl.pum.screensort`. Logi sesji są pod `sessions`.
* Baza `postgres18` jest **oddzielna od developerskiego kontenera**. Nie kopiujemy
  automatycznie jego danych, nie kasujemy wolumenu, nie zmieniamy starego repo.
  Pierwsze uruchomienie instalatora pokazuje pusty katalog; wybierz folder i skanuj.
* Hasło lokalnej bazy jest losowe, przechowywane w danych użytkownika. To nie sejf
  odporny na złośliwy program uruchomiony na tym samym koncie Windows.
* Usługi słuchają tylko na `127.0.0.1`; desktop wymaga tokenu na każdym żądaniu.
  Python ma osobny token. Nie oznacza to, że lokalny HTTP sam w sobie zastępuje
  wszystkie zabezpieczenia. Nie udostępniaj tego jako serwera publicznego.
* Modele są w instalatorze i pracują offline. Pakiet jest duży; CPU inference
  i ładowanie modeli nadal kosztują czas i pamięć. GPU/CUDA nie zostało wdrożone.
* Brak automatycznej klasyfikacji kategorii, nazw, eksportu/kopiowania i analizy
  całego folderu. Analiza jest dla wybranego pojedynczego obrazu.
* Obecnie wspieramy pakiet Windows x64. Weryfikacja na maszynie developerskiej
  nie zastępuje próby na czystej maszynie Windows/VM bez zainstalowanych runtime'ów.
* Oryginały są wyłącznie czytane. Nie dodano rename, move ani delete.

## Budowanie na komputerze developera

Wymagane do budowy: JDK 25, Node/npm, Rust + narzędzia MSVC i Python/uv. Te narzędzia
nie są wymaganiami użytkownika instalatora. Najpierw zainstaluj przypięte zależności
`analysis-service` i pobierz model poleceniami z jego README.

Z katalogu repo, w PowerShell:

```powershell
.\scripts\build-windows.ps1 -JdkPath 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
```

Skrypt buduje JAR, tworzy runtime Java przez `jlink`, składa przenośnego Pythona,
kopiuje model i same składniki serwerowe PostgreSQL. Sprawdza SHA-256 archiwów
z `packaging/windows/runtime-lock.json`, importy Pythona i uruchomienie binariów.
Poprzedni wygenerowany runtime przenosi do `.release/backups`, nie kasuje go.
Źródła, baza developerska i zdjęcia nie są materiałem instalatora.

`-StageOnly` pomija NSIS, `-SkipBackendBuild` używa już zbudowanego JAR-a.
Ten ostatni przełącznik jest tylko do świadomej pracy nad pakowaniem.
Wynik NSIS znajduje się w `frontend/src-tauri/target/release/bundle/nsis/`.
WebView2 jest dołączany jako oficjalny offline installer. Instalacja jest per-user,
z kompresją zlib (szybsza budowa, większy plik niż przy LZMA),
bez tworzenia usługi bazy systemowej. Pliki runtime, modele i `.release` są ignorowane
przez Git; na GitHub trafia kod, lockfile, dokumentacja i skrypt budowania.

Test kontrolny uruchamia kontekst Tauri i jego rzeczywisty resolver zasobów,
bez WebView. Nie zastępuje ścieżki runtime'u ręcznym argumentem:

```powershell
.\frontend\src-tauri\target\release\screensort-desktop.exe --startup-check `
  --data-dir "$PWD\.release\smoke-NOWY-UNIKALNY-KATALOG"
```

Katalog danych testu musi być nowy. Test generuje własny PNG, sprawdza skan,
OCR, wizję, miniaturę, zapis i odczyt po restarcie oraz nienaruszone bajty/mtime.
Wynik trafia obok danych do `*.report.json`. Nie czyta zdjęć użytkownika.
To test integracji pakietu, nie test klikania UI, jakości modelu ani pełny audyt.
Skrypt budowania wykonuje go automatycznie przed pakowaniem NSIS. Starszy tryb
`--smoke-test --runtime-dir ... --data-dir ...` pozostaje do osobnej diagnostyki,
ale sam nie potwierdza poprawności normalnego startu aplikacji.
