# Aktualizacje w aplikacji — 0.2.2

To pierwszy instalator zawierający updater. Wersja 0.2.1 i starsze nie potrafią
samodzielnie go doinstalować. Potrzebna jest jedna instalacja 0.2.2; kolejne
wydania Core są sprawdzane, pobierane i instalowane z interfejsu ScreenSort.
Nie dodano jeszcze aktualizacji różnicowych ani automatycznego rollbacku bazy.

## Zachowanie

Przy uruchomieniu wydania Windows updater sprawdza manifest w tle, z limitem
15 sekund. Brak sieci nie blokuje katalogu ani nie wymusza aktualizacji.
Informacja o wersji jest dostępna w prawym dolnym rogu. Nowa wersja ma własne
powiadomienie; użytkownik decyduje o pobraniu, a następnie o restarcie.
W developerskim `tauri dev` instalowanie aktualizacji jest wyłączone.

Pobieranie pokazuje rzeczywiście odebrane bajty; przy braku Content-Length
pasek jest nieokreślony. Limit wynosi 512 MiB i dotyczy Core, nie modeli AI.
Obecna implementacja oficjalnego pluginu trzyma pobierane bajty w pamięci do
instalacji; to nie jest cache trwały ani pobieranie z wznawianiem HTTP.
Zamknięcie aplikacji przed instalacją wymaga ponownego pobrania. Nie udajemy,
że aktualizacja jest różnicowa lub że nie kosztuje transferu/pamięci.

Po pobraniu plugin weryfikuje podpis Minisign oraz podpisany numer wersji.
Zmiana `version` w JSON nie pozwala wskazać starszego, poprawnie podpisanego
instalatora jako nowego wydania. Instalator musi pochodzić z HTTPS i z własnego
repozytorium dystrybucyjnego. Klient nie przyjmuje URL ani ścieżki z JavaScriptu.
Nie rozszerzono uprawnień WebView o ogólne komendy pluginu updatera.

Przycisk instalacji jest blokowany przez skan, analizę i robocze formularze.
Niezależnie od UI Rust zamyka dopływ nowych żądań API i sprawdza uwierzytelniony
`GET /_internal/update-ready`. Java sprawdza faktycznie aktywne zadania skanu,
analizy pojedynczej i analizy folderu, również gdy ich panel został zamknięty.
Żądania będące w toku blokują tę próbę; można ją ponowić po ich zakończeniu.
Potem następuje kontrolowane zatrzymanie własnych procesów i PostgreSQL.
Dopiero wtedy updater uruchamia NSIS. NSIS uruchamia nową aplikację po instalacji.

Jeśli start instalatora się nie powiedzie, UI daje możliwość restartu aplikacji.
Nie usuwa bazy ani zdjęć. Instalator Core nie zawiera Pythona/modeli;
dotychczasowe pakiety w lokalnych danych użytkownika pozostają osobno.
Podpis updatera nie jest certyfikatem Authenticode i nie usuwa ostrzeżeń SmartScreen.

## Przepływ kodu

```text
main → UpdateProvider → check_app_update (Rust) → manifest HTTPS
                        ↓ nowsza wersja
UpdatePrompt → download_app_update → limit + podpis + podpisana wersja
             → install_app_update
                → prepare_update → zamknięcie przyjmowania żądań
                                  → Java update-ready → brak aktywnych zadań
                → desktop::stop → updater.install → NSIS → nowy ScreenSort
```

`frontend/src/desktop/updates/` to status, UI i blokady formularzy.
`frontend/src-tauri/src/updates.rs` posiada prawdziwy obiekt `Update`, bajty
zweryfikowanego pliku oraz kontrolę instalacji. Token publikujący nigdy tam
nie trafia. `DesktopLifecycle` i trzy `hasActiveJob()` dostarczają minimalny
wewnętrzny odczyt gotowości, nie nowy publiczny endpoint dla frontendu.

## Publikacja i dwa różne sekrety

`screensort` jest prywatnym repo źródłowym, w którym działa CI. Publiczne
`screensort-releases` przechowuje tylko instalatory, podpisy, manifest i licencje.

- `TAURI_SIGNING_PRIVATE_KEY`: klucz podpisujący; ustawiony jako Actions secret
  prywatnego repo. Do programu trafia wyłącznie klucz publiczny.
- `RELEASES_TOKEN`: osobny fine-grained PAT z `Contents: Read and write` tylko dla
  `Pum2A/screensort-releases`, zapisany jako sekret w `Pum2A/screensort`.
  Służy jedynie do publikacji. Nie dołącza się go do `.exe` i nie podaje AI.

Ustawienia tokena: GitHub → Settings konta → Developer settings → Personal access
tokens → Fine-grained tokens → Generate new token. Resource owner: Pum2A;
Only select repositories: screensort-releases; Contents: Read and write.
Ustaw datę wygaśnięcia i odnawiaj token. Następnie w repo screensort:
Settings → Secrets and variables → Actions → New repository secret,
nazwa `RELEASES_TOKEN`, wartość w polu Secret. Nie używaj Variables ani pliku `.env`.

Push taga `desktop-v0.2.2` uruchamia workflow wydania z tego commita bez zmiany
gałęzi main. Zwykły push kodu uruchamia testy, nie publikuje produktu.
Workflow ponownie uruchamia CI, buduje Core i sprawdza prawdziwy start Tauri na
oddzielnej bazie. `create-update-manifest.ps1` kryptograficznie weryfikuje plik
narzędziem `examples/verify-update.rs`, zanim przygotuje manifest.

Wersjonowane wydanie `v0.2.2-preview` zawiera instalator i jego `.sig`.
Kanał `updates-preview` zawiera `latest.json` wskazujący to konkretne wydanie.
Najpierw publikuje się gotowe wydanie, dopiero potem aktualizuje kanał.
Nie używamy `releases/latest`, ponieważ w repo są również wydania pakietów AI,
a ten endpoint nie jest właściwym wyborem dla osobnego kanału przedpremierowego.

Lokalny klucz znajduje się w ignorowanym `.release/update-signing/screensort.key`.
Dostęp do pliku ograniczono do konta właściciela. Zachowaj zaszyfrowaną kopię
offline; utrata klucza utrudnia dalsze zaufane aktualizacje. Nie commituj go,
nie dołączaj całego `.release` do wydania, nie pokazuj klucza w czacie/logach.

Przyszłe większe zmiany wymagają osobnego planu migracji danych i kompatybilności
silnika analizy. Sam updater nie gwarantuje zgodności dowolnych wersji modeli/bazy.

## Weryfikacja tego wydania

Lokalnie: 58/58 testów frontendu, kontrola typów produkcji i fixture, lint oraz
21/21 testów Rusta. Test gotowości Javy sprawdza brak aktywności i blokowanie
każdego z trzech rodzajów pracy. Testy menedżerów i autoryzacji są wykonywane
osobno, bez prywatnej bazy: łącznie 16/16 testów Javy w tym zestawie.

Skrypt budowania przeszedł prawdziwą ścieżkę startu Tauri z izolowanym PostgreSQL:
skan, miniatura, zachowanie danych po restarcie i niezmieniony oryginał.
Instalator 0.2.2 oraz podpis powstały bez Pythona/modeli.
Narzędzie weryfikujące zaakceptowało właściwy plik, odrzuciło oczekiwaną wersję
0.2.3 zamiast podpisanej 0.2.2 oraz odrzuciło zmianę pierwszego bajtu strumienia.
Oryginalny instalator nie został w tym negatywnym teście zmodyfikowany.

UI sprawdzono osobno na fikcyjnych stanach: pobieranie z licznikiem bajtów,
blokada instalacji przez edycję, odblokowanie po jej zakończeniu i błąd.
Scenariusz: `http://127.0.0.1:5184/tests/fixtures/update-preview.html` przy
uruchomionym `npm run workspace:preview`.
Nie przeprowadzono automatycznej instalacji na profilu właściciela ani
pełnego testu aktualizacji starego zainstalowanego klienta przez internet.
Starszy klient nie zawiera updatera; wymaga pierwszej instalacji 0.2.2.

## Diagnostyka wydania na GitHub Actions

Pierwsza budowa 0.2.2 skompilowała aplikację, ale została zatrzymana przez test
rzeczywistego startu. Nie opublikowała instalatora ani manifestu. Skrypt pokazuje
teraz powód z raportu testu, a workflow zachowuje raport i dwa wskazane logi bazy
syntetycznego testu przez 7 dni w prywatnym repozytorium. Nie wysyła plików
konfiguracji sesji, hasła bazy, logu Javy ani zdjęć.

Runner Windows otrzymuje jawny dostęp do własnego workspace dla swojego SID.
PostgreSQL po obniżeniu uprawnień nie może polegać na prawach grupy Administrators;
ten przypadek opisuje również [CI PostgreSQL](https://github.com/postgres/postgres/blob/master/.github/workflows/pg-ci.yml).
Zmiana ACL dotyczy wyłącznie jednorazowej maszyny CI, nie komputera użytkownika.
Test uruchomienia nadal jest obowiązkowy; jego wynik rozstrzyga o publikacji.

Runner uruchamia polecenia jako administrator, a PostgreSQL jawnie odmawia pracy
serwera z takim tokenem. Dlatego test uruchomienia korzysta z tymczasowego zwykłego
konta lokalnego tworzonego **wyłącznie na maszynie GitHub Actions**. Konto ma prawo
zapisu do swojego syntetycznego sandboxa, nie do kodu ani runtime, i jest usuwane
po zakończeniu testu. Instalator ani aplikacja nie tworzą takich kont na komputerze
użytkownika. Lokalna budowa nadal uruchamia test na bieżącym koncie, bez podnoszenia
uprawnień. ScreenSort należy uruchamiać normalnie, nie jako administrator.
