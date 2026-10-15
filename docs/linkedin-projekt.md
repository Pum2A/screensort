# ScreenSort: materiały do LinkedIn

Stan: 15 października 2026. Teksty opisują istniejącą wersję przedpremierową,
nie gotowy organizer. Nie zostały opublikowane na profilu.

## Sekcja „Projekty”

Nazwa projektu:

ScreenSort | Ogarnij chaos w screenshotach

Opis do wklejenia:

Tworzę ScreenSort, lokalną aplikację desktopową, która ma pomóc odnaleźć sens w setkach screenshotów zapisanych pod losowymi nazwami. Zamiast otwierać każdy plik osobno, użytkownik wybiera folder, przegląda galerię i może odczytać tekst oraz opis zawartości wybranego obrazu.

Obecna wersja skanuje foldery w tle, pokazuje postęp i zapisuje katalog w PostgreSQL. Ponowny skan rozpoznaje pliki o niezmienionych metadanych i pomija ich ponowne dekodowanie oraz haszowanie. Galeria ma stronicowanie, miniatury, podgląd i szczegóły plików. Lokalne modele wykonują OCR oraz analizę wizualną, a wyniki są zapisywane i dostępne także po restarcie aplikacji. Oryginały pozostają bez zmian.

Główna logika działa w Javie i Spring Boot. React z TypeScriptem odpowiada za interfejs, a Tauri i Rust za okno desktopowe, natywny wybór folderu oraz uruchamianie lokalnych procesów. Osobny serwis Python/FastAPI udostępnia RapidOCR i Florence-2. Dane przechowuję przez JPA/Hibernate, a schemat bazy rozwijam migracjami Flyway. Docker i Testcontainers służą do developmentu i testów; użytkownik instalatora Windows nie potrzebuje Dockera ani narzędzi developerskich.

Kolejny etap to propozycje nazw i kategorii, ich ręczne zatwierdzanie oraz tworzenie uporządkowanych, zweryfikowanych kopii w osobnym folderze. Obecnie nie są to jeszcze działające funkcje. Równolegle rozwijam lżejszą dystrybucję aplikacji: pobieranie pakietu analizy na żądanie jest zaplanowane, ale nie zostało wdrożone.

Projekt rozwijam iteracyjnie, z naciskiem na zrozumiały przepływ danych, testy, pomiary wydajności i bezpieczną pracę z lokalnymi plikami.

## Krótszy opis

ScreenSort to rozwijana przeze mnie aplikacja desktopowa do pracy z chaosem w screenshotach. Łączy lokalne skanowanie folderów, trwały katalog PostgreSQL, galerię obrazów oraz OCR i analizę wizualną. Java/Spring Boot odpowiada za logikę, React/TypeScript za UI, Tauri/Rust za desktop, a Python/FastAPI za lokalne modele. Kolejny etap obejmuje propozycje nazw i kategorii, ręczne zatwierdzanie oraz bezpieczne kopiowanie. Oryginały pozostają bez zmian. Projekt jest na etapie działającej wersji przedpremierowej.

## Wpis pokazujący postęp

Ile masz screenshotów, których nazwy nic Ci już nie mówią?

Od tego problemu zacząłem ScreenSort. Buduję aplikację, w której wybierasz folder, widzisz swoje obrazy w galerii i możesz sprawdzić, jaki tekst oraz zawartość rozpoznają lokalne modele.

Mam już działający przepływ: skan folderu, zapis katalogu, podgląd obrazu, OCR, analiza wizualna i zapis wyniku w bazie. Wszystko lokalnie, bez wysyłania zdjęć do chmurowego API analizy. Ponowne skanowanie wykorzystuje poprzednie metadane, zamiast zawsze wykonywać całą ciężką pracę od początku.

Technicznie jest to połączenie Javy i Spring Boot, PostgreSQL, Reacta z TypeScriptem, Tauri/Rusta oraz osobnego serwisu Python/FastAPI z RapidOCR i Florence-2.

To jeszcze nie jest gotowy organizer. Następny krok to zamienić rozpoznaną treść na sensowne propozycje nazw i kategorii, oddać decyzję użytkownikowi i dopiero po akceptacji tworzyć uporządkowane kopie. Nie chcę, żeby aplikacja samodzielnie ruszała oryginały.

Na materiale pokazuję aktualny interfejs na wygenerowanych danych demonstracyjnych. Skanowanie i odpowiedzi modeli są rzeczywiste. Projekt nadal rozwijam, więc chętnie poznam opinie o takim sposobie pracy ze screenshotami.

#Java #SpringBoot #React #Python #PostgreSQL #Tauri #BuildInPublic

## Nagłówek / dopisek przy nazwisku

Creator of ScreenSort | Making sense of screenshot chaos

Alternatywa bardziej techniczna:

Building ScreenSort | Java, Spring Boot & local AI

## Umiejętności do podpięcia do projektu

Wybierz te, o których potrafisz już opowiedzieć na przykładzie własnego kodu:
Java, Spring Boot, PostgreSQL, SQL, JPA, Hibernate, REST APIs, TypeScript,
React, Python, FastAPI, Git. Tauri, Rust i Docker możesz dodać jako kolejne,
zależnie od tego, które fragmenty rzeczywiście znasz.

## Materiały i widoczność

Repozytorium: https://github.com/Pum2A/screensort

Repozytorium pozostaje prywatne. Osoba bez dostępu nie zobaczy README ani plików
po kliknięciu tego linku. Do publicznego LinkedIn dołącz bezpośrednio film lub
zrzuty z `docs/media`, a link do prywatnego kodu podawaj tylko z tą informacją.
Nie zmieniono widoczności repo ani nie opublikowano wpisu na LinkedIn.

Gotowe ujęcia i film opisuje [przewodnik prezentacji](presentation/README.md).
Nie dodawaj do publicznego posta pełnego instalatora 0.1.1 jako stabilnej wersji:
to duża, niepodpisana dystrybucja przedpremierowa, bez testu na czystej VM.

Nie deklarujemy jeszcze gotowych kategorii, propozycji nazw, wyszukiwania po treści,
bezpiecznego kopiowania, synchronizacji między komputerami, GPU ani konkretnych
procentów przyspieszenia. Metadane rozmiaru i czasu nie dowodzą zgodności bajtów.
