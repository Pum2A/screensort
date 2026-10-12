# Jeden obraz: przygotowanie, OCR i analiza wizualna

Aktualizacja: 2026-10-12. Dotyczy Pythona w `analysis-service`, nie skanera Javy.

## Co działa, a co jest jeszcze przed nami

Przygotowanie PNG/JPEG i prawdziwy OCR CPU są sprawdzone. Koordynator
`ImageAnalyzer` istnieje; jego połączenia i obsługa błędów są testowane
z zastępczym silnikiem wizji. Nie jest to potwierdzenie działania Florence.
Kod kandydata `VisionAnalyzer` został zachowany, ale nie zainstalowano jeszcze
PyTorch/Transformers ani nie pobrano jego modelu. HTTP nadal ma tylko health;
nie ma klienta Javy, zapisu analizy, GPU ani wyników analizy w galerii.

## Dlaczego rozdzieliliśmy metody

Dotychczas `OcrRecognizer.recognize` sam otwierał i przygotowywał obraz.
Gdyby wizja robiła to samo, mielibyśmy dwa dekodowania tych samych bajtów.
Teraz przygotowanie ma własną funkcję, a oba silniki pożyczają jej wynik.
Nadal mają własne przekształcenia do wejścia swoich modeli, więc to nie jest
obietnica braku wszystkich kopii danych ani konkretnego przyspieszenia.

| Miejsce | Wejście | Wyjście | Odpowiedzialność |
|---|---|---|---|
| `image/preparation.py`: `prepare_image` | Ograniczone bajty | Obraz RGB | Sprawdzenie i dekodowanie obrazu. |
| `ocr/recognizer.py`: `recognize` | Bajty + opcje | `OcrResult` | Zgodne z dotychczasową konsolą samodzielne OCR. |
| `ocr/recognizer.py`: `recognize_prepared` | Gotowy RGB + opcje | `OcrResult` | Rozpoznanie tekstu bez ponownego otwierania. |
| `vision/analyzer.py`: `analyze` | Gotowy RGB | `VisualResult` | Kandydat do opisu sceny, jeszcze nieuruchomiony. |
| `image/analyzer.py`: `analyze` | Bajty + opcje | `ImageAnalysisResult` | Połączenie kroków i powiązanie z oryginalną zawartością. |

To funkcje jednego lokalnego procesu, nie trzy nowe mikroserwisy. `image/`
oznacza przygotowanie i workflow jednego obrazu; nie katalog obrazów użytkownika.
Python nie przyjmuje ścieżek systemowych przez API i nie dostaje repozytoriów JPA.

## Główna metoda koordynatora

```python
def analyze(
    self,
    image_bytes: bytes,
    options: OcrOptions,
) -> ImageAnalysisResult:
    with closing(prepare_image(image_bytes)) as image:
        source_sha256 = sha256(image_bytes).hexdigest()

        ocr_result = self._ocr.recognize_prepared(image, options)
        visual_result = self._vision.analyze(image)

    return ImageAnalysisResult(
        source_sha256=source_sha256,
        ocr=ocr_result,
        visual=visual_result,
    )
```

1. `image_bytes` to dokładna zawartość wejściowego pliku, nie jego ścieżka.
2. `prepare_image` odrzuca puste wejście, zbyt wiele bajtów, inny rzeczywisty
   format i przekroczone wymiary. Dopiero potem dekoduje piksele.
3. Wynik RGB jest niezależny od otwartego źródła i obrazów pomocniczych.
4. `closing` wywoła `image.close()` po obu analizach, również przy wyjątku.
   Zwykły context manager obrazu Pillow zamyka źródło, ale nie niszczy jego
   bufora pikseli; dlatego jawnie zamykamy też obrazy pomocnicze.
5. SHA-256 dotyczy oryginalnych bajtów. Nie hashujemy obrazu po dodaniu białego
   tła, bo jego reprezentacja nie jest już zawartością wejściowego pliku.
6. `_ocr` i `_vision` to silniki otrzymane w konstruktorze. Tworzymy ich
   instancje raz poza `analyze`, zamiast od nowa dla każdego pliku.
7. Oba wywołania dostają ten sam obiekt `image`. Na razie pracują kolejno;
   nie zakładamy, że silniki są bezpieczne do współdzielenia między wątkami.
8. Po zamknięciu obrazu pozostają opisy wyników, nie jego piksele.
9. Pydantic potrafi zamienić wynik na JSON przez `result.model_dump_json()`.
   Samo utworzenie obiektu jeszcze nie wysyła HTTP ani nie zapisuje go w bazie.

## Zachowane wywołanie OCR w konsoli

```python
def recognize(self, image_bytes: bytes, options: OcrOptions) -> OcrResult:
    with closing(prepare_image(image_bytes)) as image:
        return self.recognize_prepared(image, options)
```

Ten wrapper sam posiada i zamyka przygotowany obraz. `recognize_prepared`
pożycza obraz od wywołującego i go nie zamyka: za chwilę może używać go wizja.
To zasada własności zasobu, nie funkcja Springa.

Stałe OCR pozostają dostępne, np. `OcrRecognizer.MAX_IMAGE_BYTES` do ograniczenia
odczytu w konsoli, ale wartości są teraz zdefiniowane w `image/preparation.py`.
Wizja i OCR korzystają także ze wspólnego sprawdzenia RGB i wymiarów.

`from __future__ import annotations` i `if TYPE_CHECKING` w koordynatorze służą
opisom typów bez ładowania PyTorch tylko po to, by zaimportować ten plik.
Nie umożliwiają uruchomienia prawdziwego modelu bez jego zależności.

## Limity i czas

Zachowaliśmy 50 MiB zakodowanych bajtów, 40 mln pikseli i 20 000 px na bok.
50 MiB oznacza bajty, nie bity. Obraz RGB z 40 mln pikseli to około 120 MB
samego bufora pikseli; obrazy pomocnicze i modele zwiększają zużycie RAM.
To początkowe limity polityki, a nie zmierzony limit całego procesu.

Nieprzezroczysty RGB nie wymaga budowania kanału alfa i białego tła. PNG
z przezroczystością nadal wymaga poprawnego złożenia na białym tle, także
gdy przezroczysty kolor zapisano w metadanych RGB, a nie jako kanał RGBA.

`ocr.duration_ms` po refaktorze nie zawiera wspólnego przygotowania obrazu.
Pierwszy OCR może nadal zawierać koszt lazy loading. Z tego powodu nie porównuj
starego i nowego czasu tego pola jako benchmarku całego workflow.
Przed oceną wydajności dodamy jawny pomiar całej analizy oraz przygotowania.

## Błędy i kolejne etapy

Pusty tekst OCR może być poprawnym wynikiem: nie rozpoznano napisów. Wyjątek
silnika to inna sytuacja i nie jest zamieniany na pusty, udany wynik.
Aktualny koordynator przerywa na błędzie OCR lub wizji i zwalnia obraz.
Przed HTTP ustalimy niezależne statusy/błędy częściowe, żeby udany tekst nie
ginął, gdy opis wizualny się nie powiedzie. `visual=None` służy teraz zachowaniu
przykładów OCR-only, nie ukrywaniu awarii.

Najbliższy krok: uzgodnić zależności wizji, pobrać przypięty model do lokalnego
cache i uruchomić Florence na jednym obrazie. Następnie zabezpieczony endpoint:
autoryzacja, limit uploadu przed odczytem, ograniczona praca w toku, gotowość
modeli, błędy i timeouty. Dopiero potem Java i osobne zadania analizy.

## Sprawdzenie

Testy jednostkowe używają obrazów utworzonych w pamięci i jawnych atrap silników.
Sprawdzają formaty, limity przed dekodowaniem, piksele przezroczyste, własność
obrazów, jeden krok przygotowania, kolejność OCR/wizji, hash bajtów i JSON.
Test integracyjny OCR jest opt-in; generuje czytelne napisy i blokuje sieć.
Nie przechowujemy prywatnych obrazów jako fixture i nie zmieniamy ich na dysku.

Weryfikacja 2026-10-12: 40/40 testów z włączonym testem rzeczywistego OCR;
lint i formatowanie Ruff poprawne, lockfile zgodny, wheel i sdist zbudowane
offline. Wynik rzeczywistego OCR jest taki sam jak przed refaktorem:
`ScreenSort OCR test` i `Order number 12345`. To sprawdzenie poprawności
na jednym syntetycznym przykładzie, nie benchmark ani pełna ocena jakości.

Instrukcja poleceń: [README serwisu](../analysis-service/README.md).
Zasady zasobów: [Pillow — lifecycle](https://pillow.readthedocs.io/en/stable/reference/open_files.html).
Model wizualny: [Transformers — Florence-2](https://huggingface.co/docs/transformers/model_doc/florence2).
