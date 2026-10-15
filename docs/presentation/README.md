# ScreenSort: zestaw do prezentacji projektu

Materiały przygotowane 15 października 2026. Teksty do sekcji „Projekty”,
krótszego opisu i wpisu są w [linkedin-projekt.md](../linkedin-projekt.md).

## Co można dołączyć

Do README polecane są teraz krótkie demonstracje jednej funkcji, nie poprzedni
49-sekundowy montaż plansz. Każda pętla ma 2,5 sekundy, kliknięcie, ruch kursora
i krótki powrót do początku. Wariant MP4 nadaje się także do montażu wpisu na LinkedIn.

| Plik | Zastosowanie |
| --- | --- |
| [feature-explore.gif](../media/feature-explore.gif) / [MP4](../media/feature-explore.mp4) | Wybór obrazu i panel szczegółów, pętla 2,5 s |
| [feature-preview.gif](../media/feature-preview.gif) / [MP4](../media/feature-preview.mp4) | Otwarcie podglądu, pętla 2,5 s |
| [screensort-preview.mp4](../media/screensort-preview.mp4) | Starszy, dłuższy przegląd projektu, materiał dodatkowy |
| [screensort-preview.gif](../media/screensort-preview.gif) | Starszy montaż plansz, teraz schowany w rozwijanej sekcji README |
| [catalog.jpg](../media/catalog.jpg) | Skan zakończony, sześć obrazów w galerii i jeden odrzucony plik |
| [preview.jpg](../media/preview.jpg) | Powiększony podgląd i szczegóły pliku; pełna lokalizacja zwinięta |
| [analysis.jpg](../media/analysis.jpg) | Pełny widok strony z zapisaną analizą |
| [analysis-result-detail.jpg](../media/analysis-result-detail.jpg) | Fragment odpowiedzi Florence i RapidOCR |
| [rescan.jpg](../media/rescan.jpg) | Ponowny skan: sześć znanych, niezmienionych obrazów, bez nowych rekordów |
| [workflow.svg](../media/workflow.svg) | Edytowalny diagram architektury; planowane etapy są oddzielone |
| [workflow.jpg](../media/workflow.jpg) | Diagram w formacie zdjęcia do wgrania na LinkedIn |
| [sample-input.png](../media/sample-input.png) | Wygenerowany obraz użyty do rzeczywistej analizy |

MP4 ma 49 sekund, rozdzielczość 1920 × 1080 i kodek H.264; waży około 589 kB.
GIF ma szerokość 880 px i waży około 323 kB. To lekkie materiały prezentacyjne,
nie pliki instalacyjne ani próbki porównywania wydajności.

Film jest montażem rzeczywistych ujęć interfejsu z podpisami, nie ciągłym
nagraniem ekranu. Długość ujęcia nie odpowiada czasowi operacji. Nie służy do
porównywania wydajności. Nie ma lektora, muzyki ani materiałów osób trzecich.

## Pochodzenie i zakres demonstracji

Krótkie pętle powstały z serii rzeczywistych klatek interfejsu w osobnej sesji
`.release/portfolio-motion-20261015`. Nie podmieniano API ani stanów aplikacji.
Kursor, impuls kliknięcia, stabilizacja kadru, delikatny ruch kamery i przejścia
są elementami montażu, nie deklaracją wdrożenia takich animacji w aplikacji.
W podglądzie rozmyto pełną ścieżkę źródłową. Pętle pokazują przeglądanie i podgląd,
nie wykonanie OCR w 2,5 sekundy. Nie zmieniano kodu frontendu ani backendu.

Wszystkie nowe zrzuty przedstawiają aktualny, zbudowany interfejs React.
Przeglądarkowa sesja działała z prawdziwą Javą, osobnym PostgreSQL i lokalnym
Pythonem z rzeczywistymi modelami. Nie podmieniano wyników API ani treści strony.

Wygenerowano sześć syntetycznych obrazów: przykładowy błąd połączenia z bazą,
notatki o kolekcjach, plan projektu, fikcyjny paragon, krajobraz i planszę produktu.
Dodano jeden plik tekstowy do odrzucenia. Dane znajdowały się w izolowanym
`.release/portfolio-media-20261015`, nie w bazie ani folderze zdjęć właściciela.

Na pierwszym skanie aplikacja pokazała 6 nowych obrazów i 1 odrzucenie. Na drugim
6 znanych bez zmian i 1 odrzucenie. Analizowano `Screenshot_001.png`.
Florence zwrócił opis, a RapidOCR odczytał tekst. Java zapisała odpowiedź w bazie.
Widoczne czasy modeli dotyczą tego jednego przykładu, nie ogólnego benchmarku.
Odczyt kontrolny API potwierdził 6 rekordów i zgodność SHA-256 analizy z wejściowym
PNG. Wynik bez sekretów: [demo-verification.json](demo-verification.json).

Materiały nie pokazują natywnego pickera: ujęcia wykonano w trybie przeglądarkowym.
Tauri używa tego samego frontendu, ale ma natywny wybór folderu i inny transport.
Nie przedstawiaj zrzutów jako dowodu przetestowania instalacji na czystym Windows.
Panel „Analiza · DEMO” z fikcyjnymi propozycjami nazw nie był używany w materiale.

Przykładowy błąd „Connection refused” jest treścią analizowanego obrazu, nie błędem
działającego backendu. Dane paragonu są fikcyjne. Nie pokazano prywatnych zdjęć,
haseł ani tokenów. Pełną ścieżkę w podglądzie zwinięto przed końcowym zrzutem.

## Układ krótkiej prezentacji

1. Problem: screenshoty o losowych nazwach.
2. Działający skan i trwały katalog.
3. Podgląd obrazu bez zmiany oryginału.
4. Lokalny OCR i wizja, odpowiedź zapisana przez Javę.
5. Ponowny skan korzystający z wcześniejszych metadanych.
6. Docelowo: nazwy, kategorie, akceptacja i zweryfikowane kopie. Wyraźnie „plan”.

Do rozmowy technicznej warto dopowiedzieć, dlaczego HashMap pasuje do wyszukiwania
znanych ścieżek, dlaczego streaming SHA-256 ogranicza pamięć, po co ograniczono
liczbę workerów i dlaczego Python nie dostaje dowolnej ścieżki użytkownika.
Nie trzeba przedstawiać tego jako rozproszonej platformy mikroserwisowej.

## Publikacja

Repozytorium nadal jest prywatne. Aktualizacja README nie daje dostępu rekruterowi.
Na LinkedIn możesz samodzielnie wgrać MP4 oraz zdjęcia. Nie opublikowano wpisu,
nie zmieniono widoczności repo i nie utworzono publicznego wydania z instalatorem.
Przed kolejnymi nagraniami ponownie sprawdź, czy na ekranie nie ma danych osobowych.
