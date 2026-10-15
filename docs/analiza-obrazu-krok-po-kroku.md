# Jeden obraz: przygotowanie, OCR i analiza wizualna

Aktualizacja: 2026-10-15. Dotyczy Pythona w `analysis-service`, nie skanera Javy.

## Co działa, a co jest jeszcze przed nami

Przygotowanie PNG/JPEG, prawdziwy OCR i Florence CPU są sprawdzone. Koordynator
`ImageAnalyzer` ma testy jednostkowe z zastępczymi silnikami i integrację z oboma
prawdziwymi modelami offline. Opcjonalne zależności wizji oraz przypięty model
zostały przygotowane. [Uruchomienie Florence](florence-pierwsze-uruchomienie.md).
HTTP nadal ma tylko health;
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
| `vision/analyzer.py`: `analyze` | Gotowy RGB | `VisualResult` | Rzeczywisty krótki opis sceny przez Florence CPU. |
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
    *,
    run_ocr: bool = True,
    run_visual: bool = True,
) -> ImageAnalysisResult:
    if not run_ocr and not run_visual:
        raise ValueError("Select OCR or visual analysis.")

    ocr: OcrAnalysis = SkippedAnalysis()
    visual: VisualAnalysis = SkippedAnalysis()

    with closing(prepare_image(image_bytes)) as image:
        source_sha256 = sha256(image_bytes).hexdigest()

        if run_ocr:
            try:
                ocr_result = self._ocr.recognize_prepared(image, options)
            except MemoryError:
                raise
            except Exception as exc:
                logger.error("OCR_FAILED [%s]", type(exc).__name__)
                ocr = FailedAnalysis(
                    error=AnalysisError(
                        code="OCR_FAILED",
                        message="Text recognition failed.",
                    )
                )
            else:
                ocr = CompletedAnalysis[OcrResult](result=ocr_result)

        if run_visual:
            try:
                visual_result = self._vision.analyze(image)
            except MemoryError:
                raise
            except Exception as exc:
                logger.error("VISUAL_FAILED [%s]", type(exc).__name__)
                visual = FailedAnalysis(
                    error=AnalysisError(
                        code="VISUAL_FAILED",
                        message="Visual analysis failed.",
                    )
                )
            else:
                visual = CompletedAnalysis[VisualResult](result=visual_result)

    return ImageAnalysisResult(
        source_sha256=source_sha256,
        ocr=ocr,
        visual=visual,
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

## Co zmienia obsługa niezależnych wyników

Wcześniej wyjątek OCR przerywał metodę przed wizją. Wyjątek wizji uniemożliwiał
oddanie wcześniej rozpoznanego tekstu. Teraz każdy krok ma osobny wynik:

| Status | `result` | `error` | Znaczenie |
|---|---|---|---|
| `COMPLETED` | `OcrResult` albo `VisualResult` | `null` | Silnik zwrócił poprawny wynik; nie jest to ocena trafności. |
| `FAILED` | `null` | Kod i bezpieczny komunikat | Silnik zgłosił zwykły wyjątek. |
| `SKIPPED` | `null` | `null` | Wywołujący wyłączył ten krok. |

`CompletedAnalysis[T]` jest opakowaniem na jeden typ danych. `T` zastępujemy
przez `OcrResult` albo `VisualResult`, podobnie jak typ w javowym `List<Path>`.
`OcrAnalysis` i `VisualAnalysis` dopuszczają tylko trzy warianty. Pydantic
wybiera wariant według `status`, przez `Field(discriminator="status")`.
Nie przyjmie np. statusu `FAILED` bez błędu lub z udanym wynikiem.
`extra="forbid"` odrzuca nieznane pola; `frozen=True` blokuje przypisywanie
do pól opakowania. Nie jest to głęboka niezmienność wszystkich zagnieżdżonych danych.

`*` wymusza podawanie flag po nazwie: `run_visual=False`. Ustawienie obu
flag na `False` jest błędem jeszcze przed przygotowaniem obrazu. Flagi
wybierają wykonanie kroku, nie zmieniają ładowania modeli w konstruktorze.
Wartość początkowa `SkippedAnalysis()` pozostaje tylko dla niezamówionego kroku.

Każdy `try` obejmuje tylko wywołanie silnika. `else` uruchamia się po udanym
wywołaniu i tworzy typowany wynik. Jeśli silnik zwróci nieprawidłowy obiekt,
walidacja tego opakowania przerwie metodę: to błąd kontraktu kodu, nie sukces
ani ukryta porażka inferencji. Nie łapiemy błędów przygotowania obrazu jako
błędów OCR/wizji. Nie przechwytujemy też przerwania `KeyboardInterrupt`;
`MemoryError` rzucamy dalej, aby nie uruchamiać drugiego ciężkiego silnika.

W logu zapisujemy tylko kod i nazwę typu wyjątku, nie jego treść, traceback,
napisy OCR, bajty czy ścieżkę pliku. Publiczny `error.message` jest stały.
Nie oznacza to ochrony przed awarią całego procesu lub dowolnymi logami bibliotek.

Zmiana formatu jest jawna: zamiast `result.ocr.text` używamy
`result.ocr.result.text` po sprawdzeniu `result.ocr.status == "COMPLETED"`.
Zamiast `visual=None` zwracamy obiekt `SKIPPED`. Wywołanie obu kroków
`analyzer.analyze(image_bytes, OcrOptions())` nie zmieniło się.
Pydantic >= 2.11 obsługuje używaną składnię generyczną Pythona 3.12+;
minimum zapisano w `pyproject.toml` i `uv.lock`, bez aktualizacji wersji modeli.

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

`ocr.result.duration_ms` nie zawiera wspólnego przygotowania obrazu.
Pierwszy OCR może nadal zawierać koszt lazy loading. Z tego powodu nie porównuj
starego i nowego czasu tego pola jako benchmarku całego workflow.
Konsola mierzy całość w `runs[].total_ms`; osobnego czasu przygotowania
nie dodaliśmy. Nieudane lub pominięte kroki nie mają udawanego `duration_ms`.

## Błędy i kolejne etapy

Pusty tekst OCR może być poprawnym wynikiem: nie rozpoznano napisów. Wyjątek
silnika to inna sytuacja i nie jest zamieniany na pusty, udany wynik.
Zwykłe wyjątki silników dają niezależne `FAILED`, bez utraty udanej części.
Obraz jest zamykany także przy porażce obu silników, braku pamięci i przerwaniu.
Wynik nie oznacza, że proces umie odzyskać pracę po awarii kodu natywnego.

Florence jest już uruchomiony na jednym obrazie oraz w integracji offline.
Najbliższy krok do wspólnego uzgodnienia: zabezpieczony endpoint:
autoryzacja, limit uploadu przed odczytem, ograniczona praca w toku, gotowość
modeli, błędy i timeouty. Dopiero potem Java i osobne zadania analizy.

## Sprawdzenie

Testy jednostkowe używają obrazów utworzonych w pamięci i jawnych atrap silników.
Sprawdzają formaty, limity przed dekodowaniem, piksele przezroczyste, własność
obrazów, jeden krok przygotowania, kolejność OCR/wizji, hash bajtów i JSON.
Testy koordynatora sprawdzają też błędy pojedyncze/podwójne, pominięcie kroku,
pusty tekst jako sukces, bezpieczne komunikaty i przerwanie przy braku pamięci.
`test_analysis_schemas.py` sprawdza kombinacje statusów, sprzeczne dane,
rozróżnienie typów wyniku, JSON Schema, serializację i zamrożone opakowania.
Test integracyjny OCR jest opt-in; generuje czytelne napisy i blokuje sieć.
Nie przechowujemy prywatnych obrazów jako fixture i nie zmieniamy ich na dysku.

Weryfikacja 2026-10-12: 40/40 testów z włączonym testem rzeczywistego OCR;
lint i formatowanie Ruff poprawne, lockfile zgodny, wheel i sdist zbudowane
offline. Wynik rzeczywistego OCR jest taki sam jak przed refaktorem:
`ScreenSort OCR test` i `Order number 12345`. To sprawdzenie poprawności
na jednym syntetycznym przykładzie, nie benchmark ani pełna ocena jakości.

Weryfikacja 2026-10-15 po dodaniu niezależnych wyników: 88/88 testów,
w tym rzeczywisty OCR i Florence offline na syntetycznym obrazie. Ruff lint,
formatowanie i `uv lock --check --offline --no-python-downloads` poprawne.
Lockfile zmienił tylko minimalną wersję Pydantic; nie aktualizowano modeli.
Nie zmieniono Javy, Reacta, bazy ani źródeł, nie dodano endpointu analizy.

Instrukcja poleceń: [README serwisu](../analysis-service/README.md).
Zasady zasobów: [Pillow — lifecycle](https://pillow.readthedocs.io/en/stable/reference/open_files.html).
Model wizualny: [Transformers — Florence-2](https://huggingface.co/docs/transformers/model_doc/florence2).
