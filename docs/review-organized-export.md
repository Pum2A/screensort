# Zapis uporządkowanych kopii — kod do przeglądu

Po zakończonej analizie otwórz „Zapisz i uporządkuj”, wybierz folder i kliknij
„Zapisz uporządkowaną kopię”. Java zapisze zatwierdzone obrazy w nowym folderze
ScreenSort, w podfolderach kategorii. Nazwa każdej kopii pochodzi z decyzji
użytkownika. Nie zmieniamy pliku źródłowego ani jego wpisu w katalogu.

```text
OrganizationPlanPreview — wybrane miejsce + lista zatwierdzonych decyzji
  → OrganizationExport — POST z ID analizy folderu i miejscem docelowym
    → ExportController → ExportManager
      → ExportStore.create — zapisuje migawkę aktualnych decyzji w PostgreSQL
      → jeden worker
        → sprawdzenie, czy decyzja nadal jest aktualna
        → OrganizedFileCopier — odczyt + SHA-256 + zapis buforem 64 KiB
        → nazwa końcowa, dopiero gdy zawartość się zgadza
        → zapis wyniku pojedynczego pliku
  ← GET statusu co 800 ms → licznik, problemy i folder wynikowy
```

## Najważniejsze miejsca

| Plik | Co sprawdzić podczas code review |
| --- | --- |
| `src/main/java/pl/pum/screensort/organization/ApprovedFilesSql.java` | Wspólna reguła kwalifikowania plików do podglądu i wykonania. SHA analizy, katalogu, nazwy i decyzji muszą się zgadzać, tak samo wersja nazwy. |
| `src/main/java/pl/pum/screensort/organization/export/ExportStore.java` | Migawka całej zatwierdzonej listy przez INSERT SELECT. Kolejne pliki odczytywane pojedynczo, krótkie transakcje, postęp zachowany w bazie. |
| `src/main/java/pl/pum/screensort/organization/export/OrganizedFileCopier.java` | Walidacja nazwy i ścieżek, jeden odczyt źródła małym buforem, hash i metadane przed/po odczycie, plik tymczasowy i brak nadpisywania. |
| `src/main/java/pl/pum/screensort/organization/export/ExportManager.java` | Jedno aktywne zadanie, oddzielny folder wynikowy, obsługa pominięć i przerwania pracy. Kopiowanie odbywa się poza transakcją SQL. |
| `src/main/java/pl/pum/screensort/organization/export/ExportController.java` | POST uruchamia zadanie i zwraca 202; GET odczytuje status lub stronę problemów. |
| `frontend/src/organization/export/OrganizationExport.tsx` | Odtworzenie ostatniego zadania, odpytywanie statusu, ponowienie odczytu po błędzie, blokada restartu podczas zapisu. |
| `frontend/src/organization/OrganizationPlanPreview.tsx` | Rzeczywisty wybór folderu docelowego, podgląd stron i uruchomienie zapisu. |
| `src/main/resources/db/migration/V8__create_organization_exports.sql` | Historia zadań i wyników; unikalny indeks dopuszcza jedno aktywne kopiowanie. |

## Zachowanie w konkretnych sytuacjach

Jeśli `kubek.png` już istnieje w danej kategorii, kolejna kopia otrzyma nazwę
`kubek (2).png`. Długie nazwy są skracane przy dodawaniu numeru, z zachowaniem
rozszerzenia i limitu długości. Istniejący folder ScreenSort zostaje zachowany,
a nowa sesja zapisu utworzy ScreenSort (2).

Jeśli plik zmieni się po zatwierdzeniu, eksport go pominie. Zgodność rozmiaru
nie wystarcza: podczas kopiowania liczymy SHA-256 i porównujemy z zatwierdzoną
zawartością. Udostępniamy nazwę końcową dopiero po tej kontroli. Błąd jednego
pliku nie zatrzymuje całej listy.

Po zamknięciu programu niedokończone zadanie ma status INTERRUPTED. Już zapisane
kopie pozostają dostępne. Ponowne uruchomienie tworzy nowy eksport i osobny
folder, a nie wznawia poprzedniej kopii w połowie. Plik tymczasowy pozostawiony
przez nagłe zabicie procesu może zostać w poprzednim folderze wynikowym.

Przycisk zapisu, edycja decyzji, zmiana źródła i aktualizacja programu uwzględniają
aktywne kopiowanie. Po błędzie odczytu stanu można ponowić GET tego samego zadania.

## Weryfikacja

Testy `OrganizedFileCopierTest` sprawdzają bajty kopii, zachowanie oryginału,
konflikt nazw, zmianę zawartości przy takim samym rozmiarze, niedozwoloną nazwę
i długą nazwę Unicode. `ReviewJpaTest` sprawdza migawkę i jej unieważnienie,
odzyskanie statusu po przerwaniu oraz pełny przepływ HTTP → PostgreSQL → dysk.

Uruchomienie testów na osobnej, lokalnej bazie:

```powershell
./scripts/verify-native-tests.ps1 -Tests 'ReviewJpaTest,OrganizedFileCopierTest'
```

Interfejs można obejrzeć też w scenariuszu fikcyjnym `workspace-preview.html?scenario=organized`.
Żółty pasek jasno oznacza symulację. Ten podgląd nie zapisuje plików na dysku;
rzeczywisty eksport obsługuje endpoint Javy w normalnie uruchomionej aplikacji.
