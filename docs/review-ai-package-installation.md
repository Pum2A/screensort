# Code review: mały Core i instalacja AI na żądanie

To opis etapu po `da79df5`. Nie zmienia algorytmów OCR, Florence ani zapisu wyników w Javie. Dodaje sposób dostarczenia istniejącego silnika użytkownikowi, który zainstalował sam Core. Nie jest jeszcze wyborem wielu modeli ani obsługą GPU.

## Co widzi użytkownik

Uruchamia ScreenSort, a katalog i prywatny PostgreSQL startują bez Pythona. W sekcji „Pakiet AI” widzi rozmiar pobrania oraz przyciski „Pobierz pakiet AI” i „Wybierz pobrany ZIP”. Pierwszy pobiera oficjalny plik, drugi otwiera natywne okno Windows. W obu przypadkach instalujemy dokładnie tę samą wersję, a nie dowolne biblioteki wskazane przez użytkownika.

Pobieranie jest osobnym etapem z postępem i anulowaniem. Potem osobny postęp pokazuje rozpakowanie. Dopiero po zakończeniu pojawia się możliwość uruchomienia AI. To nie to samo co pobranie: uruchomienie tworzy proces Pythona i ładuje modele do pamięci. Katalog w tym czasie nadal działa.

## Przepływ, który warto przejść w debuggerze

```text
React: AiPackagePanel → invoke('install_ai_package', {id, fromFile})
Rust:  install_ai_package → PackageManager.begin → spawn_blocking
       → pobranie HTTPS LUB natywny picker ZIP
       → kontrola liczby bajtów i SHA-256
       → rozpakowanie w nowym katalogu tymczasowym
       → kontrola zawartości → installed.json → atomowa zmiana nazwy katalogu
React: odczyt ai_package_status → przycisk Uruchom AI
Rust:  start_analysis_runtime → Owner.start_analysis
       → packages.runtime wybiera ukończony pakiet → python.exe
Python: dotychczasowy lokalny silnik OCR + Florence
Java:   dotychczasowy prywatny handshake i zapis wyników
```

## `packaging/windows/ai-packages.json`: skąd wiemy, co można uruchomić

Ten plik jest wkompilowany w aplikację przez `include_str!`, a nie pobierany jako niezaufana instrukcja z internetu. `id` wybiera profil, `version` identyfikuje wydanie, `url` wskazuje publiczny asset, a `sha256` jest oczekiwanym skrótem **całego ZIP**. Nie jest to hash zdjęcia z katalogu.

`archiveBytes` oznacza dokładny rozmiar pobrania. `unpackedBytes` i `fileCount` ograniczają rozpakowanie. `requiredFiles` określa pliki, bez których instalacja nie jest kompletna. Dzięki temu sam fakt istnienia folderu `python` nie oznacza jeszcze sukcesu.

Katalog zawiera jeden przetestowany profil CPU. Aktualizacja katalogu wymaga nowej kompilacji aplikacji. To świadomy kompromis: prostsza granica zaufania zamiast aktualizacji dowolnych modeli z niepodpisanego zdalnego manifestu. W przyszłości można wprowadzić podpisany katalog i osobne profile OCR/wizji.

## `packages/catalog.rs`: walidacja zanim zaczniemy

`catalog()` parsuje JSON i sprawdza wersję schematu oraz unikalność identyfikatorów. `PackageSpec.validate()` odrzuca niepoprawne identyfikatory, rozmiary, hash i wymagane ścieżki. Są to zabezpieczenia przed błędem w samym wydaniu, nie walidacja zdjęć.

`download_url()` dopuszcza tylko HTTPS i repozytorium `Pum2A/screensort-releases`. React przekazuje wyłącznie `id`: nie może podać innego serwera ani polecenia instalacyjnego.

`portable_path()` sprawdza nazwy ZIP jak nazwy Windows. Odrzuca m.in. `../`, ścieżki bezwzględne, backslash, strumienie `plik:sekret`, urządzenia `NUL`/`COM1`, puste segmenty i końcowe kropki. Nie „naprawia” takich nazw przez usunięcie fragmentu: przerywa instalację.

`directory()` tworzy katalog z ID, wersji i fragmentu oczekiwanego hasha. Różne wydania nie nadpisują się nawzajem.

## `packages/download.rs`: bajty z internetu nie są jeszcze instalacją

`Client::builder()` ustawia normalną kontrolę certyfikatów TLS, timeout połączenia, odczytu i całego pobierania. Na Windows używamy magazynu certyfikatów systemu przez native TLS. Nie wyłączamy weryfikacji certyfikatów. Przekierowania mają limit i listę dozwolonych hostów GitHub, bo pliki Releases są fizycznie serwowane z innego hosta niż strona wydania.

W `download_from()`:

```rust
let chunk = response.chunk().await?;
```

Nie oznacza to „wczytaj cały ZIP”. `chunk()` oddaje kolejną porcję odpowiedzi; `await` oddaje oczekiwanie na sieć do runtime. W rzeczywistym kodzie błędy są tłumaczone na komunikat dla użytkownika.

```rust
output.write_all(&chunk)?;
digest.update(&chunk);
```

Pierwsza linia zapisuje otrzymane bajty do pliku tymczasowego. Druga aktualizuje obliczany hash **tych samych bajtów**. Nie trzeba po pobraniu ponownie czytać całego pliku, żeby obliczyć hash. Licznik bajtów jest jednocześnie ograniczeniem rozmiaru i źródłem postępu.

Po końcu odpowiedzi porównujemy dokładny rozmiar i wynik SHA-256 z katalogiem. Błąd sieci lub niezgodność kończy operację przed rozpakowaniem. Odpowiedzi HTTP inne niż 200 również nie stają się paczką. Token GitHub nie jest potrzebny: asset jest publiczny. Zdjęcia nie są wysyłane do GitHub.

Na tym etapie nie ma HTTP Range/resume. Ponowienie zaczyna pobieranie od początku. Anulowanie jest sprawdzane między porcjami; oczekiwanie na zablokowany odczyt ogranicza timeout, nie obiecujemy natychmiastowego przerwania każdego syscalla.

## `packages/archive.rs`: dlaczego nie wywołujemy po prostu `extract()`

`copy_verified()` jest lokalnym odpowiednikiem pobierania. Czyta ZIP przez bufor 128 KiB, zapisuje jego własną kopię i jednocześnie oblicza hash. Rozpakowujemy potem tę kopię: zmiana pliku wybranego przez użytkownika nie podmieni już zawartości między sprawdzeniem i użyciem.

`extract_verified()` otwiera ZIP dopiero po potwierdzeniu jego hasha. Dla każdego wpisu sprawdza ścieżkę, dowiązania, dozwolony katalog główny i kolizję nazw bez rozróżniania wielkości ASCII. Pliki powstają przez `create_new(true)`: istniejący plik nie jest po cichu nadpisywany. Nie uruchamiamy programu z archiwum podczas instalacji.

Podczas odczytu kontrolowane są rzeczywiste bajty, deklarowany rozmiar wpisu, CRC, całkowity rozmiar i liczba plików. Chroni to również przed błędnie spakowaną lub nadmiernie rozwijającą się paczką. Wydajność: bufor ma stały rozmiar, a utworzone katalogi są zapamiętywane w `HashSet`, żeby nie wykonywać tych samych kontroli ich przodków dla każdego z ponad 20 tysięcy plików.

`plain_directory()` przed rozpoczęciem sprawdza przodków katalogu instalacji, w tym junction/reparse point na Windows. Nie traktujemy istniejącego przekierowania jak bezpiecznego katalogu pakietu.

## `packages/mod.rs`: kto steruje instalacją

`PackageManager` ma dwa różne narzędzia synchronizacji. `Mutex<Progress>` chroni spójny zestaw pól postępu: kilka pól zmienia się razem. `AtomicBool` jest pojedynczą flagą anulowania, którą worker może tanio sprawdzać. Nie są to modele AI ani kolejne mikroserwisy.

`begin()` zabrania dwóch instalacji naraz i ustawia początkowy stan. `update()` zapisuje postęp. `finish()` kończy stan również po błędzie. Java i PostgreSQL nie uczestniczą w pobieraniu bibliotek.

W `install()` kolejność jest ważna:

1. Sprawdź specyfikację i katalog docelowy. Nie nadpisuj istniejącej, niekompletnej wersji.
2. Sprawdź miejsce na ZIP + rozpakowany payload + 256 MiB zapasu.
3. Utwórz unikalny plik tymczasowy i pobierz/sprawdź ZIP.
4. Utwórz unikalny katalog tymczasowy na tym samym woluminie i rozpakuj.
5. Sprawdź anulowanie oraz wymagane pliki. Zapisz `installed.json`.
6. Zmień nazwę kompletnego katalogu na docelową. Dopiero wtedy resolver zobaczy wersję jako zainstalowaną.

Na Windows aktywacja ma ograniczone ponowienie (maks. 5 sekund) po błędzie blokady/dostępu do świeżych plików. Inny proces, np. skaner antywirusowy, może chwilowo trzymać DLL bez zgody na zmianę nazwy katalogu. Nie wyłączamy ochrony Windows, nie nadpisujemy innej wersji i zachowujemy kod błędu systemowego przy trwałym niepowodzeniu. Osobny test Windows blokuje plik, zwalnia go i sprawdza aktywację. UI rozróżnia aktywację oraz zwykłe anulowanie od czerwonego błędu.

`NamedTempFile` i `TempDir` sprzątają tymczasowe zasoby po zwykłym błędzie lub anulowaniu. Awaria/zabicie procesu może pozostawić osierocony staging, ale nie jest on uznawany za aktywny pakiet. Nie ma jeszcze automatycznego sprzątania po twardej awarii ani usuwania starych wersji.

`installed()` sprawdza mały receipt oraz pliki wymagane, a nie hashuje ponownie 1,3 GiB przy każdym starcie. To kompromis szybkości: chronimy dystrybucję i instalację, nie udajemy antywirusa chroniącego pliki przed innym procesem tego samego użytkownika. SHA-256 nie jest podpisem wydawcy; instalator nie ma jeszcze Authenticode.

## `desktop/mod.rs` i `processes.rs`: co zmieniło się w uruchamianiu

`install_ai_package()` przyjmuje ID i boolean `from_file`, nie ścieżkę ani URL od Reacta. Przy imporcie sam Rust otwiera natywne okno wyboru. `spawn_blocking` oddelegowuje operacje plikowe i dialog poza pętlę UI. Zakończenie workera zawsze trafia do `finish`, również przy błędzie zadania.

Instalowanie i start modeli używają wspólnej kolejności blokad. Nie wolno aktywować pakietu, gdy Python już go ładuje/używa. Zamknięcie aplikacji zgłasza anulowanie instalacji; nie usuwa bazy.

W `Owner.start_analysis()` zmieniła się podstawa ścieżki do `python.exe` i `models`. Teraz `packages.runtime()` najpierw szuka ukończonego, wspieranego pakietu w danych aplikacji. Jeśli go nie ma, nadal obsługuje wcześniejszy pełny instalator z wbudowanym Pythonem. Odczyt następuje przy kliknięciu „Uruchom AI”, więc świeżo zainstalowana paczka działa bez restartu katalogu.

Pozostały przepływ jest ten sam: lokalny Python → gotowość `/ready` → prywatne przekazanie portu i tokena do Javy → istniejące endpointy analizy. Porty/tokeny nadal nie trafiają do webview.

## React: co reviewować

`desktop/packages/state.ts` zawiera typy odpowiedzi, tłumaczenia etapów, procent i MiB. Nie mylimy bajtów z bitami. Procent pobrania i procent rozpakowania są osobnymi etapami, a nie jedną fikcyjną liczbą „całej instalacji”.

`AiPackagePanel.tsx` odczytuje stan co sekundę, wyłącza kolidujące przyciski, pokazuje błędy po ludzku oraz używa ikon Lucide i istniejącego Tailwinda. Nie uruchamia `pip` ani `fetch()` do dowolnego serwera. `DesktopStartup.tsx` osadza panel dopiero, gdy katalog jest gotowy. Zainstalowane biblioteki nie są automatycznie ładowane do RAM.

## Budowanie i publikacja

`scripts/build-ai-package.ps1` bierze tylko whitelistę `python/`, `models/` i noty licencyjne z portable runtime. Nie archiwizuje repozytorium, bazy, zdjęć ani `.git`. Odrzuca dowiązania i prywatne pliki konfiguracyjne. Pomija cache, `.pyc` i `direct_url.json`. Wynik oraz kandydat katalogu powstają w ignorowanym `.release/`. Dopiero sprawdzony hash trafia do wersjonowanego katalogu aplikacji.

`Pum2A/screensort` pozostaje prywatne. `Pum2A/screensort-releases` jest publicznym repozytorium dystrybucyjnym. ZIP zawiera wykonywalny Python oraz kod usługi niezbędny do lokalnej analizy; nie obiecujemy, że dystrybuowany program ukrywa swój kod Python.

CI sprawdza cztery części produktu niezależnie. Akcje są przypięte do konkretnych commitów. `windows-release.yml` najpierw uruchamia te kontrole, potem buduje CoreOnly i testuje rzeczywisty Tauri PathResolver na nowej testowej bazie. Do artefaktu trafiają wyłącznie jawnie wybrane pliki dystrybucyjne, nie katalog logów czy staging całego workspace.

Publikacja między repozytoriami wymaga `RELEASES_TOKEN`: fine-grained token z `Contents: write` **tylko** dla `Pum2A/screensort-releases`, zapisany jako Actions secret w prywatnym źródłowym repo. Nie wklejaj tokena do czatu, kodu ani logów. Nie kopiujemy lokalnych poświadczeń `gh` do CI. Bez tego sekretu build/artefakt działa, a żądana publikacja zatrzyma się z czytelnym błędem.

Nowy ręcznie uruchamiany workflow stanie się widoczny w Actions po dołączeniu pliku do domyślnej gałęzi. Sam push do `codex/**` uruchamia CI, nie publikuje automatycznie instalatora.

Dokumentacja źródłowa: [GitHub: secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets), [reusable workflows](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows), [artefakty](https://docs.github.com/en/actions/tutorials/store-and-share-data).

## Granice tego etapu

## Wyniki weryfikacji (2026-10-19)

Pierwszy pełny test wykrył błąd końcowego rename na Windows. Po dodaniu ograniczonego ponowienia:

* 19 testów Rust przeszło, w tym wymuszone zablokowanie/zwolnienie pliku na Windows.
* 43 testy frontendu, lint i build przeszły. Python: 102 przeszły, 2 testy rzeczywistych modeli są opt-in.
* Wszystkie cztery joby CI dla `dfc63b3` przeszły. Lokalny pełny Maven zatrzymał się na braku dostępnego Dockera; zdalny Maven w CI przeszedł. Osobno 6 testów zapisu/przeglądu przeszło na dołączonym, izolowanym PostgreSQL.
* Test Core → import ZIP → OCR + Florence → analiza folderu → restart → odczyt zapisanych wyników przeszedł przez rzeczywisty Tauri PathResolver. Oryginał syntetycznego zdjęcia nie zmienił się.
* Instalacja lokalnego ZIP zajęła 102 978 ms na tym komputerze, w tym kontrola SHA i rozpakowanie 21 387 plików. To nie jest czas instalacji Core ani uniwersalny benchmark. Nie obiecujemy błyskawicznego pobierania 713 MiB na każdym łączu.

Raport lokalny: `.release/package-import-release-20261019-02.report.json` (ignorowany przez Git, bez zdjęć/danych użytkownika).
Wydanie paczki: [AI CPU 2026-10-19-2](https://github.com/Pum2A/screensort-releases/releases/tag/ai-cpu-2026-10-19-2).
Test publicznego pobierania i finalnego instalatora musi być potwierdzony osobno; powyższy test używał importu ZIP.

## Czego ten etap nadal nie robi

To nie zmienia jakości rozpoznawania Roblox/brandów. Nie dodaje ponownego trenowania, GPU, automatycznego przenoszenia/zmiany nazw oryginałów, wyboru innych modeli ani aktualizacji całej aplikacji z jej wnętrza. Core aktualizujemy nowym instalatorem, zachowując dotychczasowy katalog danych. Wyniki testów i adresy wydań są podawane przy handoffie, nie wynikają z samego istnienia tych plików.
