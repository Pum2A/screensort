# Poprawka startu Windows 0.1.1

Data: 2026-10-15. Zgłoszenie właściciela: po instalacji aplikacja zatrzymuje się
na „Przygotowujemy prywatną bazę PostgreSQL”, a inicjalizacja kończy się błędem.

## Przyczyna

Wersja 0.1.0 otrzymywała od Tauri ścieżkę z prefiksem Windows `\\?\`.
`initdb` rozpoznawał swój katalog, zamieniał separatory na `/`, a potem próbował
sprawdzić wersję sąsiedniego `postgres.exe` przez wewnętrzne wywołanie powłoki.
Ścieżka zaczynająca się od `//?/C:/...` nie działała w tym wywołaniu.

Sam plik serwera był obecny, miał poprawną wersję 18.6 i uruchamiał się pod
zwykłą ścieżką. Problem nie wymaga doinstalowania JDK, Dockera ani PostgreSQL.
W zgłoszonym przypadku katalog `postgres18` nie zdążył powstać. Plik hasła
i logi zostały zachowane; poprawka nie wymaga ich kasowania.

Wcześniejsze testy pakietu uruchamiały analizę z ręcznie przekazaną ścieżką
`--runtime-dir`. Ominęły istotną część zwykłego startu — resolver ścieżek Tauri.
Sukces tych testów był prawdziwy, ale zakres testu był niewystarczający.

## Co zmieniono

* `desktop/paths.rs`: `dunce::simplified` zamienia prefiks tylko wtedy, gdy
  można bezpiecznie zachować znaczenie ścieżki. Nie używamy prostego odcinania
  czterech znaków. Niezamienialne ścieżki specjalne kończą się czytelnym błędem.
* `processes::start`: normalizuje katalog runtime'u i danych **przed** zbudowaniem
  ścieżek programów, modeli, konfiguracji i bazy. Dotyczy także ponownego startu
  i późniejszego kontrolowanego zatrzymania PostgreSQL.
* `runtime_path`: wspólne ustalanie lokalizacji runtime'u dla aplikacji i testu.
  Błędy ustalania katalogów trafiają do stanu UI, zamiast pozostawiać spinner.
* `--startup-check`: tworzy rzeczywisty kontekst Tauri i korzysta z jego
  `resource_dir()`. Nie przyjmuje ręcznego zamiennika runtime'u. Tylko katalog
  danych testu jest jawnie wskazany i musi być nowy. Nie tworzy WebView ani nie
  modyfikuje profilu lub bazy użytkownika; pomija blokadę pojedynczej instancji,
  aby nie podłączyć się przypadkiem do otwartej aplikacji właściciela.
* Skrypt budowania najpierw tworzy program bez instalatora, następnie wykonuje
  ten test startu. Dopiero poprawny raport pozwala rozpocząć pakowanie NSIS.
* Wersję programu i instalatora podniesiono do 0.1.1. Identyfikator aplikacji
  pozostał ten sam, więc aktualizacja ma zachować katalog danych użytkownika.

## Weryfikacja

Stary program 0.1.0 uruchomiony na izolowanych danych, ale z prefiksem runtime'u
`\\?\`, odtworzył dokładnie błąd inicjalizacji. Nie używano bazy użytkownika.

Sześć testów Rusta sprawdza zwykłe ścieżki, ścieżkę Tauri z prefiksem,
spacje i Unicode, zachowanie zwykłego UNC, odrzucenie niebezpiecznego
upraszczania oraz dotychczasową allowlistę API. Wszystkie przeszły.

Pełny test `--startup-check` przeszedł 2026-10-15. Uruchomił prawdziwe dołączone
PostgreSQL, Javę i Pythona, zeskanował wygenerowany PNG, wykonał OCR/wizję,
pobrał miniaturę, zapisał wynik, zrestartował procesy i odczytał zapisany wynik.
Nie wymagał narzędzi developerskich w PATH. Raport
`.release/startup-resolver-after-01.report.json` zawiera:

```json
{
  "bundledRuntimes": true,
  "ocr": true,
  "passed": true,
  "persistedAfterRestart": true,
  "resourcePathHadVerbatimPrefix": true,
  "scan": true,
  "sourceUnchanged": true,
  "tauriResourceResolver": true,
  "thumbnail": true,
  "visual": true
}
```

`resourcePathHadVerbatimPrefix=true` potwierdza, że odtworzono problematyczny
rodzaj ścieżki, a nie tylko zwykły start z ręcznie wpisanym katalogiem.
Osobny test nowego programu z wymuszonym prefiksem i runtime'em starej instalacji
także przeszedł (`.release/startup-prefix-after-01.report.json`). Z tego runtime'u
tylko czytano; dane testowe były osobne. Przeszły również 31 testów frontendu,
kompilacja release oraz kontrola formatowania Rusta. Raporty i dane testów
pozostają lokalnie, poza Gitem; powyżej zapisano wynik bez sekretów.

## Aktualizacja z 0.1.0

Gotowy pakiet: `frontend/src-tauri/target/release/bundle/nsis/ScreenSort_0.1.1_x64-setup.exe`.
Budowanie NSIS zakończyło się poprawnie; metadane pliku wskazują 0.1.1.
Rozmiar: 1 042 524 483 bajty. SHA-256 instalatora:

```text
f802f095afa598491640be252129b63073d79e9ac930d9c9075c1092f3ceac84
```

Zamknij starą aplikację i uruchom instalator `ScreenSort_0.1.1_x64-setup.exe`
na obecnej instalacji. Nie trzeba odinstalowywać programu ani kasować danych,
hasła lub logów. Identyfikator aplikacji i lokalizacja bazy nie zmieniły się.
W zgłoszonym przypadku poprawiony start może dokończyć pierwszą inicjalizację,
korzystając z zachowanego pliku hasła.

To nadal nie jest pełny audyt ani automatyczny test klikania interfejsu.
Nie przeprowadzono instalacji aktualizacji w istniejącym profilu właściciela
ani testu na czystej maszynie Windows. Test startu nie tworzył WebView.
Nie dodano usuwania/naprawiania katalogu bazy, migracji danych z Dockera,
zmiany nazw ani zapisu do oryginalnych zdjęć.
