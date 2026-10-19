# Przegląd zmian: AI na żądanie i analiza całego katalogu

Ten dokument dotyczy zmian po `c31771c`. Nie zastępuje wcześniejszego przewodnika.
To pierwszy działający fragment planu, nie ukończony organizator ani nowy model wizualny.

## Co możesz teraz sprawdzić w aplikacji

Katalog startuje bez procesu Pythona. „Uruchom pakiet AI” dopiero uruchamia dołączony
Python, OCR i Florence. „Analizuj wszystko” obejmuje wszystkie zapisane pliki danego
folderu, niezależnie od strony galerii. Postęp i propozycje pozostają w PostgreSQL.
Pauza działa między obrazami; trwające obliczenie może się jeszcze zakończyć.
Po restarcie przerwane zadanie jest wstrzymane, a nie automatycznie uruchamiane.

Źródła są tylko odczytywane. Nie ma jeszcze kopiowania, przenoszenia, zmiany nazw na
dysku, pobierania modeli ani wyboru profili AI. Dotychczasowe zatwierdzanie nazwy
pojedynczego obrazu pozostaje osobną funkcją.

## Start: dlaczego Rust nie uruchamia już wszystkiego

Otwórz `frontend/src-tauri/src/desktop/processes.rs`, funkcję `start`.
Lista wymaganych plików obejmuje Javę, PostgreSQL i backend, ale nie Pythona.
Powstaje baza i proces Javy. Po sprawdzeniu `/_internal/ready` funkcja oddaje
`Owner`: obiekt, który przechowuje uchwyty do naszych procesów i pilnuje ich zamknięcia.
Nie czeka na modele i nie uruchamia ich w tle.

W `desktop/mod.rs` pole `owner` używa asynchronicznego mutexa. Mutex oznacza, że
tylko jedna operacja naraz zmienia właściciela procesów. Wersja asynchroniczna
pozwala innemu zadaniu czekać bez blokowania wątku podczas przygotowywania AI.

`start_analysis_runtime` najpierw ustawia `analysis_starting`, potem zleca pracę
w tle. Drugie kliknięcie nie tworzy drugiego startu. `Owner.start_analysis`
uruchamia istniejący pakiet, czeka na jego chronione `/ready` i dopiero wtedy
przekazuje adres oraz token Javie przez `/_internal/analysis-connection`.
Port i sekret nie trafiają do Reacta. Most do webview nie pozwala wywołać tej trasy.

`PythonAnalysisClient` przechowuje `volatile Endpoint`. To jedna para klient HTTP +
token, publikowana innym wątkom jako całość. Metoda `analyze` bierze lokalną kopię
tej pary, więc nie miesza adresu starego procesu z nowym tokenem. Bez połączenia
zwraca czytelny błąd 503, ale Spring i katalog nadal działają.

Próba startu uszkodzonego pakietu nie może tworzyć kolejnych procesów bez limitu.
Po takiej awarii wymagany jest restart aplikacji. Brak pakietu nie uszkadza bazy.

Alternatywa: uruchamiać Python od razu i dopiero później pokazać katalog. Odrzucamy
ją tutaj, bo nadal zajmuje CPU/RAM użytkownikowi, który chce tylko przeglądać pliki.

## Analizuj wszystko: konkretny przykład z 55 obrazami

`OrganizationPanel` wywołuje `startBatch(folderPath, ocr, visual)`. Nie przesyła
tablicy 50 widocznych ID. `BatchController` otrzymuje folder i opcje.
`BatchManager.start` waliduje folder, tworzy zadanie i zwraca jego UUID, zanim
zakończy się praca modelu. `UUID` identyfikuje uruchomienie, nie obraz.

`BatchStore.create` wykonuje `INSERT ... SELECT`: PostgreSQL sam zapisuje 55 ID,
nazw i hashy z katalogu do tabeli elementów zadania. Nie ściągamy 55 pełnych encji
do Javy tylko po to, by ponownie wysłać je do bazy. To migawka katalogu w momencie
zlecenia, nie obietnica, że pliki wciąż istnieją na dysku.

`BatchManager.run` powtarza następujący przepływ:

```text
czy zadanie nadal RUNNING?
  → pobierz jeden PENDING i oznacz RUNNING
  → odczytaj rzeczywisty obraz i sprawdź hash względem migawki
  → Python: OCR / opis wizualny
  → sprawdź hash odpowiedzi i zapisz analizę
  → zapisz kategorię i propozycję nazwy
  → następny element
```

`BatchStore.next` używa krótkiej transakcji i blokady wybranego wiersza.
Transakcja kończy się przed odczytem obrazu i wywołaniem modelu. Nie trzymamy
połączenia z bazą przez kilkanaście sekund inferencji.

`isRunning` pyta wyłącznie o status zadania. Nie zlicza wszystkich elementów przed
każdym plikiem. Pełne liczniki pobiera UI podczas odświeżania postępu.

`AnalysisProcessor` jest wspólny dla analizy pojedynczego obrazu i całego folderu.
`synchronized` zapewnia jeden aktywny przebieg w tym obiekcie. To celowy limit
lokalnego silnika, nie próba przyspieszania tysiącem wątków. Oba menedżery mają
ograniczone przyjmowanie zadań; nie tworzą kolejki wszystkich bajtów obrazów w RAM.
`beforeSave` jest callbackiem: pojedynczy job ustawia nim etap SAVING tuż przed
zapisem. Job folderu nie potrzebuje tego dodatkowego komunikatu.

Zmieniony plik dostaje błąd i nie jest analizowany jako stara wersja. Błąd jednego
obrazu nie kończy pozostałych. Niedostępny silnik 503 wstrzymuje całe zadanie,
zostawiając bieżący plik do ponowienia; nie produkujemy 10 000 identycznych błędów.
Nieudane oba etapy modelu oznaczają błąd pliku. Częściowy wynik zachowujemy z uwagą.

Przy starcie `recover` zamienia przerwane zadania na PAUSED, a niedokończone
elementy na PENDING. Ukończone elementy pozostają ukończone. To wykonanie co najmniej
raz: awaria między zapisaniem analizy a checkpointem może spowodować ponowienie
jednego obrazu, ale nie utratę oryginału. Nowy job nadal analizuje od nowa; cache
wyników według wersji modelu nie jest jeszcze wdrożony.

Dlaczego tutaj SQL/JdbcTemplate, a nie kolejne encje JPA? Potrzebujemy głównie
operacji zbiorczych, liczników i przejść statusu. Jawny SQL ułatwia zobaczenie ich
kosztu. Istniejący katalog i wyniki analizy nadal używają JPA. Parametry zapytań
przekazujemy przez `?`, nie doklejamy ścieżek użytkownika do tekstu SQL.

## Co faktycznie robi kategoryzacja w tej wersji

`CategorySuggester` dostaje zapisany `AnalysisResult`, zbiera opis i tekst OCR,
dzieli je na słowa i porównuje z małymi zestawami reguł. Jedna dopasowana kategoria
daje propozycję. Brak dopasowania lub konflikt daje REVIEW, czyli „Do sprawdzenia”.
Nie dopasowujemy fragmentu `game` wewnątrz słowa `gamer`.

To przejrzysty punkt odniesienia, nie rozwiązanie trafności Florence. Opis
„cartoon character” nie zostanie magicznie zamieniony na Roblox. Nie dodano CLIP,
SigLIP, nowego treningu ani GPU. Nie ma też deklarowanych procentów pewności.
Generator nazw pozostaje dotychczasową heurystyką; jego jakość wymaga dalszej pracy.

## Jak czytać frontend

`organization/api.ts` opisuje kształt JSON i wywołania HTTP. `OrganizationPanel.tsx`
przechowuje stan widoku, odpytuje postęp i pokazuje propozycje. Baza jest źródłem
prawdy: po przeładowaniu panel odczytuje ostatnie zadanie folderu.

`useEffect` tworzy `AbortController`. Przy zmianie folderu/strony lub zamknięciu
panelu anuluje oczekiwanie i timer. Późna odpowiedź starego widoku nie podmienia
wyników nowego. Kolejne odpytywanie następuje po zakończeniu poprzedniego, nie przez
nakładające się żądania `setInterval`. Anulowanie oczekiwania HTTP nie anuluje joba.

Wyniki mają osobne strony po 50. `hasNext` ustalamy, pobierając maksymalnie 51
wierszy: 50 pokazujemy, dodatkowy potwierdza następną stronę. Nie pobieramy całej
listy do przeglądarki. Kolor czerwony oznacza błąd, bursztynowy uwagę, zielony
ukończenie bez błędów. Tekst również przekazuje stan, nie sam kolor.

Tailwind działa w nowych elementach. Importujemy theme/utilities bez Preflight,
żeby nie wywrócić istniejących paneli. Wykrywanie klas jest ograniczone do `src`,
nie do prywatnych runtime'ów. Lucide zastępuje ręcznie zapisane ścieżki SVG.
Miniatury są prostokątne i używają `object-fit: contain`, aby nie obcinać screenshotów.
Istniejące lazy loading i mechanizmy podglądu zostały zachowane.

## Uruchomienie i weryfikacja

W tej kopii roboczej zaktualizowano prywatny `runtime/backend.jar`; poprzedni JAR
zachowano w `.release/backups`. Zainstalowany wcześniej EXE nie aktualizuje się
od samego commita. Nie zbudowano nowego instalatora jako gotowego wydania.

Do sprawdzenia w tej kopii: `cd frontend`, potem `npm run tauri dev`.
Po uruchomieniu wybierz folder, zeskanuj, uruchom pakiet AI i wybierz „Analizuj wszystko”.

Na innym urządzeniu potrzebny jest świeży backend JAR i przygotowane prywatne
runtime'y zgodnie z `docs/instalator-windows.md`; samo `npm run dev` nie uruchamia
całego desktopu.

`scripts/build-windows.ps1 -CoreOnly` dodaje wariant bez Pythona i modeli.
Taki pakiet jest katalogiem bez AI; nie udaje działającego menedżera pobierania.
Podstawowa Java, PostgreSQL i konfiguracja WebView2 nadal wpływają na wielkość.

Test bazy bez Dockera: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-native-tests.ps1`.
Skrypt tworzy własny klaster pod `.release`, uruchamia BatchStoreJpaTest i regresję
NamingJpaTest, po czym zamyka serwer. Nie używa istniejącej bazy użytkownika.

Sprawdzono również prawdziwy lokalny przepływ Rust → Java → Python na wygenerowanym
PNG: OCR, Florence, analiza folderu, miniatura i trwałość po restarcie. Test UI
korzystał osobno z kontrolowanych odpowiedzi; nie jest pomiarem trafności modeli.

Wyniki weryfikacji z 2026-10-19:

| Zakres | Wynik |
| --- | --- |
| Regresja Java bez testów JPA/Integration | 136 testów, 0 błędów, 2 pominięte |
| BatchStoreJpaTest + NamingJpaTest, jednorazowy PostgreSQL | 6 testów, 0 błędów |
| Frontend | 40 testów, lint i build poprawne |
| Rust | 6 testów biblioteki, build poprawny |
| Python | 102 poprawne, 2 opcjonalne testy modeli pominięte |
| Prawdziwe modele, poza powyższym pytest | Pełny startup-check Tauri, OCR, Florence i job folderu poprawne |
| Pakiet CoreOnly bez katalogów python/models | Startup-check Tauri, skan, miniatura i restart poprawne |
| npm audit | 0 zgłoszonych podatności po aktualizacji source-map-js |

Runtime CoreOnly zajmuje 282,6 MiB, pełny 1653,9 MiB: około 83% mniej nieskompresowanych
plików. Nie zmierzono jeszcze czasu instalacji ani reprezentatywnego czasu startu.
To testy na obecnym komputerze, nie walidacja instalatora na czystym Windows.

## Co powinno być przedmiotem Twojego code review

Najpierw `processes.rs::start` i `start_analysis`: czy katalog rzeczywiście nie
potrzebuje AI? Potem `PythonAnalysisClient.connect` oraz natywna lista dozwolonych
tras: czy sekret może dotrzeć do webview?

Następnie migracja V6 → `BatchStore.create/next/recover` → `BatchManager.run` →
`AnalysisProcessor.process`: śledź jeden fileId, jego hash i przejścia statusu.

Na końcu `CategorySuggester` i `OrganizationPanel`: oddziel to, co naprawdę zwrócił
model, od naszej propozycji i od decyzji użytkownika. Ten etap jeszcze nie wykonuje
żadnej operacji organizowania na dysku.
