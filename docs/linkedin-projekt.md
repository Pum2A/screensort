# ScreenSort

## Nazwa projektu na LinkedIn

ScreenSort | Zrozum zawartość. Nie zgaduj po nazwie.

## Opis projektu do wklejenia

Screenshot_20261015_182304.png. Nazwa niewiele mówi. Zawartość już tak.

ScreenSort przywraca kontekst obrazom zapisanym pod przypadkowymi nazwami. Wybierasz folder, przeglądasz galerię i uruchamiasz lokalne rozpoznawanie tekstu oraz analizę wizualną. Wynik trafia do trwałego katalogu, nie znika po zamknięciu aplikacji. Oryginały pozostają nietknięte.

Za prostą interakcją stoi konkretny przepływ: walidacja plików, skanowanie przyrostowe, SHA-256, zadania w tle i zapis w PostgreSQL. Java 25 i Spring Boot koordynują proces. React z TypeScriptem prezentuje wyniki. Tauri i Rust łączą interfejs z desktopem, a osobny serwis Python/FastAPI uruchamia RapidOCR i Florence-2. Analiza odbywa się lokalnie, bez przesyłania obrazów do chmurowego API AI.

Kolejny etap: od rozpoznanej treści do propozycji nazw i kategorii, ręcznej akceptacji i zweryfikowanych kopii w osobnym folderze.

ScreenSort jest działającą wersją przedpremierową, rozwijaną w kierunku bezpiecznego organizera screenshotów.

## Krótki opis

ScreenSort przywraca kontekst screenshotom o przypadkowych nazwach. Łączy przyrostowe skanowanie folderów, trwały katalog PostgreSQL, galerię oraz lokalny OCR i analizę wizualną. Java/Spring Boot odpowiada za logikę, React/TypeScript za interfejs, Tauri/Rust za desktop, a Python/FastAPI za modele. Oryginały pozostają nietknięte. Następny etap to propozycje nazw, kategorii i zweryfikowane kopie po akceptacji użytkownika.

## Post z krótkim demo

Pamiętasz, co było na screenshocie. Nie pamiętasz, jak nazywa się plik.

Właśnie ten problem rozwiązuję w ScreenSort.

Wybierasz folder. Otwierasz obraz. Odczytujesz tekst i opis zawartości. Bez wysyłania zdjęć do chmurowego AI i bez zmieniania oryginałów.

Pod spodem pracują Java 25 i Spring Boot, przyrostowy skaner z kontrolowaną współbieżnością, strumieniowe SHA-256 oraz PostgreSQL. React i TypeScript tworzą interfejs, Tauri/Rust zapewnia warstwę desktopową, a Python/FastAPI udostępnia lokalne modele RapidOCR i Florence-2.

Katalog i wyniki analizy zostają w bazie. Ponowny skan wykorzystuje znane metadane, zamiast za każdym razem dekodować i haszować wszystkie obrazy od początku.

Na krótkich ujęciach pokazuję aktualne interakcje z działającej aplikacji. Dalej rozwijam to, co najważniejsze dla organizowania plików: propozycje nazw i kategorii, ich zatwierdzanie oraz bezpieczne tworzenie uporządkowanych kopii.

Najpierw zrozumieć zawartość. Potem zrobić z nią porządek.

#Java #SpringBoot #LocalAI #BuildInPublic

## Nagłówek po angielsku

Creator of ScreenSort | Making sense of screenshot chaos

## Krótki podpis do animacji

Jeden folder. Więcej kontekstu.

Wariant techniczny:

Local-first screenshot explorer. Java at the core. AI on your device.
