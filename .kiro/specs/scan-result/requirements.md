# Requirements Document

## Introduction

Funkcja **ScanResult** rozszerza istniejącą warstwę skanowania aplikacji ScreenSort. Obecnie `ImageScanner` skanuje bezpośrednie dzieci folderu źródłowego i zwraca `List<ScannedImage>`, po cichu pomijając pliki, które nie spełniają kryteriów akceptacji. W efekcie użytkownik nie wie, które pliki zostały pominięte ani dlaczego.

Celem tej funkcji jest zastąpienie cichego pomijania bogatszym wynikiem skanowania — obiektem `ScanResult`, który zawiera zarówno listę zaakceptowanych obrazów, jak i listę plików odrzuconych wraz z konkretnym, jednoznacznym powodem odrzucenia. Dzięki temu prototyp Thymeleaf (a w przyszłości inne warstwy) będzie mógł pokazać użytkownikowi pełny obraz wyniku skanowania.

Zakres tej funkcji jest celowo wąski i obejmuje wyłącznie warstwę skanowania (`pl.pum.screensort.scanning`). Funkcja **NIE** obejmuje: wystawienia REST API, wykrywania typu MIME, obliczania SHA-256, wykrywania duplikatów ani zapisu do bazy danych — to osobne, kolejne etapy. Zachowana zostaje bezwzględna zasada read-only dla folderu źródłowego.

## Glossary

- **Skaner (`ImageScanner`)**: Komponent warstwy skanowania, który przegląda bezpośrednie dzieci folderu źródłowego i klasyfikuje każdy plik jako zaakceptowany lub odrzucony.
- **Folder źródłowy**: Katalog wskazany przez użytkownika do skanowania, zwalidowany przez `ScanFolderValidator` i reprezentowany jako `AllowedRoot`. Folder źródłowy jest zawsze tylko do odczytu.
- **Wpis wejściowy**: Pojedynczy element (plik, katalog, link symboliczny) będący bezpośrednim dzieckiem folderu źródłowego, napotkany podczas skanowania.
- **Zaakceptowany obraz (`ScannedImage`)**: Wpis wejściowy, który spełnił wszystkie kryteria akceptacji (zwykły plik, wspierane rozszerzenie, dozwolony rozmiar, czytelny jako obraz).
- **Odrzucony plik (`RejectedFile`)**: Wpis wejściowy, który nie spełnił co najmniej jednego kryterium akceptacji, wraz z przypisanym powodem odrzucenia.
- **Powód odrzucenia (`RejectionReason`)**: Jednoznaczna, wyliczeniowa wartość wskazująca konkretną przyczynę odrzucenia wpisu wejściowego.
- **Wynik skanowania (`ScanResult`)**: Obiekt zwracany przez Skaner, zawierający listę zaakceptowanych obrazów oraz listę odrzuconych plików.
- **Wspierane rozszerzenie**: Rozszerzenie pliku z zbioru `png`, `jpg`, `jpeg` (porównywane bez uwzględniania wielkości liter).
- **Maksymalny rozmiar pliku**: 50 MiB (52 428 800 bajtów).

## Requirements

### Requirement 1: Zwracanie wyniku skanowania zamiast samej listy obrazów

**User Story:** Jako użytkownik prototypu, chcę otrzymać kompletny wynik skanowania, aby zobaczyć zarówno pliki zaakceptowane, jak i odrzucone.

#### Acceptance Criteria

1. WHEN skanowanie folderu źródłowego zakończy się poprawnie, THE Skaner SHALL zwrócić obiekt ScanResult zawierający listę zaakceptowanych obrazów oraz listę odrzuconych plików.
2. THE ScanResult SHALL udostępniać listę zaakceptowanych obrazów jako kolekcję elementów ScannedImage.
3. THE ScanResult SHALL udostępniać listę odrzuconych plików jako kolekcję elementów RejectedFile.
4. WHEN folder źródłowy nie zawiera żadnych wpisów wejściowych, THE Skaner SHALL zwrócić ScanResult z pustą listą zaakceptowanych obrazów oraz pustą listą odrzuconych plików.

### Requirement 2: Klasyfikacja i rejestrowanie odrzuconych plików

**User Story:** Jako użytkownik prototypu, chcę zobaczyć każdy odrzucony plik wraz z konkretnym powodem, aby zrozumieć, dlaczego dany plik nie został skatalogowany.

#### Acceptance Criteria

1. WHEN wpis wejściowy nie spełnia co najmniej jednego kryterium akceptacji, THE Skaner SHALL dodać element RejectedFile do listy odrzuconych plików w ScanResult.
2. THE Skaner SHALL przypisać każdemu odrzuconemu plikowi dokładnie jeden powód odrzucenia typu RejectionReason.
3. THE RejectedFile SHALL zawierać ścieżkę odrzuconego wpisu wejściowego oraz jego oryginalną nazwę.
4. THE RejectedFile SHALL zawierać powód odrzucenia typu RejectionReason.

### Requirement 3: Konkretne powody odrzucenia

**User Story:** Jako użytkownik prototypu, chcę, aby powody odrzucenia były jednoznaczne i rozróżnialne, aby móc odróżnić różne rodzaje problemów z plikami.

#### Acceptance Criteria

1. IF wpis wejściowy jest linkiem symbolicznym, THEN THE Skaner SHALL przypisać powód odrzucenia SYMBOLIC_LINK.
2. IF wpis wejściowy nie jest linkiem symbolicznym oraz nie jest zwykłym plikiem, THEN THE Skaner SHALL przypisać powód odrzucenia NOT_A_REGULAR_FILE.
3. IF wpis wejściowy jest zwykłym plikiem oraz jego rozszerzenie nie należy do zbioru wspieranych rozszerzeń, THEN THE Skaner SHALL przypisać powód odrzucenia UNSUPPORTED_EXTENSION.
4. IF wpis wejściowy jest zwykłym plikiem o wspieranym rozszerzeniu oraz jego rozmiar przekracza maksymalny rozmiar pliku, THEN THE Skaner SHALL przypisać powód odrzucenia FILE_TOO_LARGE.
5. IF wpis wejściowy jest zwykłym plikiem o wspieranym rozszerzeniu i dozwolonym rozmiarze oraz nie może zostać zdekodowany jako obraz, THEN THE Skaner SHALL przypisać powód odrzucenia UNREADABLE_IMAGE.

### Requirement 4: Kryteria akceptacji obrazu

**User Story:** Jako użytkownik prototypu, chcę, aby jako obrazy akceptowane były wyłącznie prawidłowe pliki graficzne, aby katalog zawierał tylko czytelne screenshoty.

#### Acceptance Criteria

1. WHEN wpis wejściowy jest zwykłym plikiem o wspieranym rozszerzeniu, o rozmiarze nieprzekraczającym maksymalnego rozmiaru pliku oraz możliwym do zdekodowania jako obraz, THE Skaner SHALL dodać odpowiadający element ScannedImage do listy zaakceptowanych obrazów w ScanResult.
2. THE Skaner SHALL porównywać rozszerzenie pliku ze zbiorem wspieranych rozszerzeń bez uwzględniania wielkości liter.
3. THE Skaner SHALL wypełnić element ScannedImage ścieżką, oryginalną nazwą, rozszerzeniem, rozmiarem w bajtach oraz datą modyfikacji wpisu wejściowego.

### Requirement 5: Zakres skanowania i zasada read-only

**User Story:** Jako właściciel aplikacji, chcę, aby skanowanie nie modyfikowało folderu źródłowego i obejmowało wyłącznie jego bezpośrednie dzieci, aby dane źródłowe pozostały nienaruszone.

#### Acceptance Criteria

1. THE Skaner SHALL przetwarzać wyłącznie bezpośrednie dzieci folderu źródłowego bez wchodzenia do podkatalogów.
2. THE Skaner SHALL wykonywać na folderze źródłowym oraz jego zawartości wyłącznie operacje odczytu.
3. WHILE Skaner przetwarza folder źródłowy, THE Skaner SHALL powstrzymać się od tworzenia jakichkolwiek plików zapisu, w tym plików tymczasowych oraz plików logów, w folderze źródłowym.
4. WHILE Skaner przetwarza folder źródłowy, THE Skaner SHALL pozostawić nazwy, lokalizacje oraz zawartość wpisów wejściowych niezmienione.
5. THE Skaner SHALL walidować folder źródłowy przez ScanFolderValidator przed odczytem jego zawartości.

### Requirement 6: Obsługa błędów odczytu pojedynczego wpisu

**User Story:** Jako użytkownik prototypu, chcę, aby problem z odczytem pojedynczego pliku nie przerywał całego skanowania, aby otrzymać wynik dla pozostałych plików.

#### Acceptance Criteria

1. IF odczyt metadanych wpisu wejściowego (rozmiar lub data modyfikacji) zakończy się błędem wejścia/wyjścia, THEN THE Skaner SHALL odrzucić ten wpis z powodem odrzucenia UNREADABLE_IMAGE oraz kontynuować przetwarzanie pozostałych wpisów wejściowych.
2. IF próba zdekodowania wpisu wejściowego jako obrazu zakończy się błędem wejścia/wyjścia, THEN THE Skaner SHALL odrzucić ten wpis z powodem odrzucenia UNREADABLE_IMAGE oraz kontynuować przetwarzanie pozostałych wpisów wejściowych.
