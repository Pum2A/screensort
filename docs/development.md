# ScreenSort: uruchomienie i weryfikacja developerska

To instrukcja dla developera. Użytkownik instalatora Windows nie potrzebuje
JDK, Node, Pythona ani Dockera. Pakowanie i jego ograniczenia opisuje
[przewodnik Windows](instalator-windows.md).

## Backend i baza developerska

Wymagane: Java 25, Maven Wrapper w repo oraz Docker Desktop z Linux containers
i Docker Compose. Komendy wykonuj z głównego katalogu repo:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Profil `local` włącza integrację Spring Boot z `compose.yaml`. Docker uruchamia
PostgreSQL, nie tworzy kodu aplikacji. Hibernate sprawdza mapowania, a Flyway
zarządza schematem. Dane developerskiego kontenera i baza desktop są oddzielne.
Testy Testcontainers potrzebują działającego Dockera i własnej tymczasowej bazy.
Nie traktuj pominięcia tych testów jako ich zaliczenia.

## React

Wymagany jest Node zgodny z wersją Vite w `frontend/package-lock.json`.
Instaluj przypięte zależności przez `npm ci`:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Otwórz `http://127.0.0.1:5173/`. Vite przekazuje `/api` do backendu na
`127.0.0.1:8080`. W przeglądarce wpisujesz ścieżkę; natywny picker działa
w oknie Tauri. Nie udostępniaj developerskiego API w sieci publicznej.

```powershell
npm.cmd run test
npm.cmd run build
npm.cmd run lint
```

Testy frontendu używają TypeScript i test runnera Node. Pliki `.test-output`
są generowane lokalnie i ignorowane przez Git.

## Analiza lokalna

Instrukcje instalacji zależności, pobierania konkretnej rewizji Florence,
losowego tokenu oraz uruchamiania serwisu są w
[analysis-service/README.md](../analysis-service/README.md).

Java musi mieć zgodne `screensort.analysis.url` i `screensort.analysis.token`.
Sam start Pythona bez konfiguracji klienta Javy nie łączy automatycznie obu usług.
W instalatorze te ustawienia i procesy konfiguruje prywatny launcher Rusta.

## Ręczna kontrola przepływu

Użyj małego folderu z obrazami, które możesz bezpiecznie pokazać. Skanowanie
dotyczy bezpośrednich wpisów folderu, nie całego dysku ani rekursji po podfolderach.

1. Pierwszy skan: poprawne obrazy są nowe, nieobsługiwane wpisy odrzucone.
2. Drugi niezmieniony skan: obrazy są znane, zachowują ID, aktualizuje się `last_seen_at`.
3. Zmień testowy obraz: zmiana rozmiaru lub czasu uruchamia ponowną weryfikację.
4. Wybierz obraz i uruchom analizę. OCR i wizja mają niezależne wyniki.
5. Uruchom aplikację ponownie. Katalog i zapisane wyniki analizy pozostają w bazie.

Przykładowy odczyt developerskiej bazy, bez jej modyfikacji:

```powershell
docker compose exec postgres psql -U screensort -d screensort -c "SELECT ar.canonical_path, fr.relative_path, fr.id, fr.last_seen_at FROM file_record fr JOIN allowed_root ar ON ar.id = fr.allowed_root_id ORDER BY ar.canonical_path, fr.relative_path;"
```

Zgodne metadane są skrótem wydajnościowym, nie dowodem niezmienności treści.
Kopiowanie w 0.2.3 ponownie sprawdza zawartość przez SHA-256. Hasła z `compose.yaml` są
wyłącznie developerskie; nie używaj ich w publicznym środowisku.

## Weryfikacja zatwierdzonych kopii

Po analizie całego folderu zatwierdź nazwy i kategorie, otwórz „Zapisz i uporządkuj”,
wybierz osobny folder docelowy i uruchom zapis. Powstaje nowy folder `ScreenSort`
z kategoriami; istniejący folder powoduje użycie kolejnego numeru. Oryginały
pozostają w dotychczasowym miejscu. Zmodyfikowany lub niedostępny plik powinien
pojawić się na liście problemów, nie jako poprawnie zapisana kopia.

Testy integracyjne mogą użyć dołączonego runtime PostgreSQL na oddzielnych,
tymczasowych danych, zamiast bazy użytkownika:

```powershell
./scripts/verify-native-tests.ps1 -Tests 'ReviewJpaTest,OrganizedFileCopierTest,DesktopUpdateReadinessTest'
```

Szczegóły i granice: [review-organized-export.md](review-organized-export.md).
Serwery `workspace:preview` i `review:preview` używają fikcyjnych danych w pamięci;
nie testują prawdziwego eksportu. Samo pobranie zmian Git nie aktualizuje
zainstalowanego `.exe`; do tego służy kanał aktualizacji w aplikacji.

## Więcej materiałów

[Architektura](diagramy-architektury.md), [struktura kodu](struktura-projektu.md),
[galeria i ograniczenia podglądów](galeria-i-podglady.md),
[ScanJob krok po kroku](scanjob-krok-po-kroku.md),
[przekazanie projektu na inne urządzenie](device-handoff.md).
