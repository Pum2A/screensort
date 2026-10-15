# Weryfikacja pakietu Windows — 2026-10-15

> Aktualizacja: wersja 0.1.0 ma potwierdzony błąd normalnego startu Windows.
> Nie używaj jej instalatora. Poniższe testy używały ręcznej ścieżki runtime'u,
> więc nie dowodziły poprawności uruchomienia przez okno Tauri.
> [Opis poprawki 0.1.1 i rozszerzonej weryfikacji](naprawa-startu-windows.md).

Zbudowano niepodpisany instalator NSIS Windows x64:
`ScreenSort_0.1.0_x64-setup.exe`.

* Rozmiar: 1 042 483 814 bajtów (około 1,04 GB / 994 MiB).
* SHA-256: `faa529c76e0e2ad9e85ac6730a257d0f98156d25e9d31a72ee20f004498df311`.
* Bundled Java: Temurin 25.0.4.1, własny obraz jlink.
* Bundled Python: oficjalny embedded CPython 3.14.8, zależności z `uv.lock`.
* Bundled PostgreSQL: 18.6, bez pgAdmin/StackBuilder i bez usługi Windows.
* Modele: RapidOCR CPU oraz przypięty Florence-2-base-ft CPU; bez pobierania
  modelu podczas uruchamiania pakietu.

## Co rzeczywiście sprawdzono

1. Zainstalowano **gotowy instalator**, nie tylko uruchomiono kod ze źródeł.
   Instalacja per-user do izolowanego katalogu testowego, bez skrótów.
2. Z tego katalogu uruchomiono test wbudowany w finalny program `.exe`.
   `PATH` zawierał wyłącznie Windows System32; wyłączono JAVA_HOME,
   PYTHONHOME i PYTHONPATH. Docker Desktop był wyłączony.
3. Launcher utworzył nowy prywatny PostgreSQL, uruchomił Javę i Pythona,
   poczekał na ich gotowość. Flyway wykonał V1–V4, Hibernate zaakceptował schemat.
4. Test sam utworzył mały biały PNG. Zlecił skan przez prawdziwe API Javy,
   pobrał stronę katalogu i ID rekordu, sprawdził miniaturę PNG.
5. Osobne zadanie analizy wykonało rzeczywisty OCR i Florence, zwróciło
   `COMPLETED` dla obu i zgodny SHA-256. Pusty OCR białego obrazu jest poprawny.
6. Zatrzymano własne procesy i uruchomiono je ponownie z tą samą bazą.
   Odczytano zapisany wynik analizy przez API, bez ponownego analizowania.
7. Porównano bajty i mtime oryginału: bez zmian. Procesy zakończyły pracę;
   PostgreSQL odnotował kontrolowane zatrzymanie.

Raport testu zainstalowanego pakietu:

```json
{
  "bundledRuntimes": true,
  "ocr": true,
  "passed": true,
  "persistedAfterRestart": true,
  "scan": true,
  "sourceUnchanged": true,
  "thumbnail": true,
  "visual": true
}
```

W trakcie prac test ujawnił brak `httpx2` w paczce oraz zbyt wczesną kontrolę
gotowości PostgreSQL. Poprawiono skład paczki i zastąpiono sam test portu
`pg_isready`. Jedna próba uruchomiona równolegle z pakowaniem zakończyła się
bez raportu; jej przyczyny nie potwierdzono. Końcowy test z zainstalowanego pakietu
przeszedł dwukrotnie w dwóch kolejnych izolowanych uruchomieniach. Przed publicznym wydaniem trzeba rozszerzyć próby stabilności,
zamykania w trakcie startu/analizy i zachowania po awarii.

## Pozostałe testy

* Python: 104 testy, w tym OCR na wygenerowanych napisach i prawdziwy Florence
  z zablokowaną siecią. Nie używano prywatnej fotografii jako testowego fixture.
* Java: 93 testy w aktualnym przebiegu bez `*JpaTest` i `*IntegrationTest`.
  Starsze raporty w `target` nie są doliczane do tego wyniku.
* React/API: 31 testów; produkcyjny build i lint poprawne.
* Rust: test allowlisty natywnego mostu poprawny; release build poprawny.
* Ruff lint/format oraz kontrola `uv.lock` poprawne.

## Czego nie dowodzi ten raport

Nie wykonano pełnych testów Testcontainers, klikania wszystkich kontrolek UI,
testu na czystej VM, próby braku WebView2 ani audytu licencji/bezpieczeństwa.
Wyłączenie runtime'ów z PATH nie odinstalowuje bibliotek systemowych z komputera.
Dołączono app-local Visual C++ CRT i sprawdzono importy bibliotek, lecz czysta VM
pozostaje ważnym kolejnym krokiem. To nie benchmark szybkości ani trafności
kategoryzacji, nie dowód obsługi GPU i nie gotowa automatyczna organizacja plików.

Instalator jest lokalnym artefaktem, nie plikiem w Git. Kod, lockfile, instrukcje
i ten raport trafiają do repo. [Uruchamianie i budowanie](instalator-windows.md).

Po sprawdzeniu odinstalowano tylko tymczasową instalację i usunięto jej wskazanie
lokalizacji. Właściwy instalator oraz raporty i testowe bazy w `.release` pozostają;
nie usuwano danych użytkownika ani starego repozytorium.
