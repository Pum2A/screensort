# Florence: rzeczywisty opis obrazu i wspólny JSON

Data: 2026-10-12. Etap developerski w lokalnym Pythonie, bez zmian Javy i bazy.

## Co uruchomiliśmy

- Python 3.14.4, RapidOCR 3.10.0, ONNX Runtime 1.31.0.
- PyTorch 2.14.1+cpu, Transformers 5.19.0, Hugging Face Hub 2.2.0.
- Florence `florence-community/Florence-2-base-ft`, revision
  `0b03b6f15a4a211370fb204aee4e7dd48887ea37`.
- CPU float32, 4 wątki PyTorch w konsoli, `<CAPTION>`, do 96 nowych tokenów,
  `num_beams=1`, `do_sample=False`. Nie ma CUDA ani pomiaru GPU.

Runtime jest opcjonalnym zestawem `vision` we własnym `.venv`. Windows/Linux
pobierają PyTorch z oficjalnego indeksu CPU. Ten wariant nie instaluje bibliotek
NVIDIA. Wersje i hashe zależności znajdują się w `uv.lock`.

## Co dodaliśmy w kodzie

`vision/model.py` zawiera wspólny identyfikator, revision i listę potrzebnych
plików. Dzięki temu pobieracz i silnik nie mogą przypadkiem wskazywać różnych
modeli. `VisionAnalyzer` nadal ładuje modele wyłącznie z lokalnego cache,
bez zdalnego kodu i z wagami safetensors.

`vision/download_model.py` to osobne, jawne polecenie. Pobiera przypięte dane
modelu, nie otrzymuje zdjęć i nie uruchamia analizy. Cache `models/` jest
ignorowany przez Git. Nie trzeba konta ani tokenu Hugging Face. Brak uprawnień
do symlinków w Windows może oznaczać większy cache, ale nie wymaga administratora.

`image/console.py` to lokalny tester wybranego pliku. Ogranicza odczyt do
50 MiB, nie zmienia źródła, ustawia lokalny tryb offline, tworzy oba silniki
raz i wykonuje wspólny `ImageAnalyzer`. Wynik JSON trafia na stdout, nie do bazy
ani automatycznie do pliku. Wypisanie prywatnego OCR jest świadomą czynnością
developerską, nie wzorcem logowania treści w przyszłym API.

## Co mierzy konsola

| Pole | Znaczenie |
|---|---|
| `initialization_ms` | Konstrukcja silników, w tym ładowanie Florence; bez wcześniejszych importów Pythona i bez pobierania plików. |
| `runs[].total_ms` | Przygotowanie, hash, OCR, wizja i składanie wyniku jednej analizy. |
| `ocr.duration_ms` | OCR bez wspólnego przygotowania; pierwsze wywołanie może zawierać lazy loading. |
| `visual.duration_ms` | Przetworzenie wejścia przez processor, generowanie i odczyt opisu. |
| `runtime.cpu_threads` | Ustawiona liczba wątków PyTorch, nie łączna liczba wątków procesu. |
| `runtime.cuda_build` | W sprawdzonym wariancie null: zainstalowany runtime CPU. |

Na jednej ręcznie wybranej fotografii 1536×2048 uzyskano około 1,54 s
inicjalizacji, 6,71 s całej pierwszej analizy i 5,59 s kolejnej. Wizja:
2,66 s / 2,73 s, OCR: 3,89 s / 2,84 s. Nie jest to benchmark katalogu,
mediana wielu obrazów ani obietnica takiego czasu dla innych plików/sprzętu.
Nie zamieszczamy tutaj zdjęcia, ścieżki użytkownika, hasha ani prywatnego OCR.
Oryginał miał identyczną zawartość, rozmiar i datę modyfikacji przed i po próbie.

Krótkie opisy są na razie po angielsku i nie wymieniają wszystkich obiektów.
Nie ma automatycznej kategorii, nazwy, tagów ani wiarygodnego procentu pewności.
Sukces inferencji nie jest dowodem prawdziwości każdego zdania modelu.
Przed wykorzystaniem do organizacji propozycje będą wymagały przeglądu.

## Testy

Weryfikacja 2026-10-12: 47/47 testów z włączonym rzeczywistym OCR i Florence,
lint oraz formatowanie Ruff poprawne. Integracja blokuje połączenia sieciowe
i używa syntetycznego obrazu z czytelnym tekstem. Sprawdza niepusty opis,
OCR, identyfikację modelu i CPU, zgodność hasha z wejściem i JSON.
To test połączenia komponentów, nie pełny test trafności wizualnej.

Powtórzenie 2026-10-14 przed commitami: ponownie 47/47 testów z włączonymi
integracjami OCR i Florence, lint i formatowanie poprawne.

Integracje są opt-in, aby zwykłe testy nie wymagały wielkiego modelu.
Polecenia instalacji, pobierania i uruchomienia:
[README serwisu](../analysis-service/README.md).

## Następny etap: Python → Java → JPA

Nie łączymy Pythona bezpośrednio z PostgreSQL. W Javie zachowujemy trwałą
tożsamość `file_record.id`, bezpieczeństwo źródła i zapis wyniku.

Proponowana kolejność do wspólnego uzgodnienia:

1. Kontrakt częściowych wyników OCR/wizji, bez zamieniania awarii w pusty sukces.
2. Zabezpieczony lokalny POST Pythona: ograniczone bajty, opcje, token
   komunikacji, limit uploadu przed wczytaniem, gotowość modeli i ograniczona
   liczba analiz. Timeout żądania nie gwarantuje zatrzymania inferencji.
3. Klient Javy otrzymuje DTO JSON, nie encję JPA. Java wybiera źródło po ID
   rekordu katalogu i sprawdza rzeczywiste bajty. Nie wysyła miniatury zamiast
   oryginału: hash miniatury nie odpowiada zawartości pliku katalogu.
4. Nowa migracja, np. V4, tworzy osobną tabelę wyniku analizy. Powiązanie:
   `file_record_id` + hash faktycznie przeanalizowanej zawartości; zapis także
   wersji silników, modelu/revision, opcji, tekstu, opisu, statusów i czasu.
   Pełny klucz ponownego wykorzystania ustalimy razem przed SQL.
5. Encja, repozytorium JPA i krótki zapis transakcyjny po obliczeniach.
   Nie utrzymujemy transakcji bazy podczas kilku sekund wywołania modelu.
6. Osobne zadanie analizy oraz odczyt zapisanych wyników dla Reacta.

Skanowanie nie uruchamia automatycznie analizy całego katalogu. `ScanJob`
pozostaje zadaniem skanu. W tym etapie nie dodano endpointu, migracji, klienta
Javy, zapisu wyników ani rzeczywistej analizy w UI. To następny wspólny krok,
nie deklaracja gotowego połączenia.

Źródła: [Transformers — Florence-2](https://huggingface.co/docs/transformers/model_doc/florence2),
[uv — instalacja PyTorch CPU](https://docs.astral.sh/uv/guides/integration/pytorch),
[przypięty model](https://huggingface.co/florence-community/Florence-2-base-ft/tree/0b03b6f15a4a211370fb204aee4e7dd48887ea37).
