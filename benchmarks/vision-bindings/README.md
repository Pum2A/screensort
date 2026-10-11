# Benchmark powiązań ONNX: Python / Java / Rust

To osobny eksperyment, nie mikroserwis ani część uruchamianej aplikacji.
Nie zmienia Springa, Reacta, Tauri, PostgreSQL ani obrazów źródłowych.

Wyniki i ich ograniczenia: [raport po polsku](../../docs/benchmark-analiza-jezyki.md).
Surowe pomiary: [results-2026-10-11.json](results-2026-10-11.json).

## Co robią pliki

| Plik | Rola |
|---|---|
| `prepare.ps1` | Pobiera publiczny model i JAR, sprawdza SHA-256, przygotowuje lokalne DLL. |
| `python_bench.py` | Uruchamia model w Pythonie. |
| `JavaBench.java` | Uruchamia ten sam model bez Springa. |
| `rust/src/main.rs` | Uruchamia ten sam model bez Tauri, przez `ort`. |
| `run.py` | Generuje identyczne wejście, uruchamia procesy kolejno, mierzy RAM/start i porównuje wyniki. |
| `requirements.txt`, `rust/Cargo.lock` | Ustalają wersje zależności eksperymentu. |

Model: MobileNet v2, `mobilenetv2-12.onnx`, FP32, ImageNet, Apache 2.0.
Silnik: ONNX Runtime **1.26.0**, CPU, jedna identyczna DLL dla wszystkich języków.
Rust: `ort` **2.0.0-rc.13** (nieoficjalny wrapper, przypięta wersja prerelease).

Nie używamy modelu jako docelowej analizy screenshotów. Nie jest OCR ani modelem
generującym opisy. Wejście to 16 syntetycznych, znormalizowanych tablic pikseli
RGB 224x224, a nie zbiór obrazów do oceny jakości rozpoznawania.

## Powtórzenie na Windows x64

Wymagane: Java 25, Rust/Cargo z działającym linkerem MSVC, Python zgodny
z przypiętymi zależnościami (pomiar wykonano na Python 3.14.4). Sieć potrzebna
tylko do pobrania bibliotek i modelu. Nie instaluj zależności globalnie.

Z katalogu `benchmarks/vision-bindings`, jeśli nie masz tych pakietów:

```powershell
rtk proxy python -m venv .runtime/venv
. .runtime/venv/Scripts/Activate.ps1
rtk proxy python -m pip install -r requirements.txt
```

Potem z głównego katalogu repozytorium, w tym samym aktywowanym środowisku:

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File benchmarks/vision-bindings/prepare.ps1
rtk proxy javac -cp benchmarks/vision-bindings/.runtime/onnxruntime-1.26.0.jar -d benchmarks/vision-bindings/.runtime benchmarks/vision-bindings/JavaBench.java
rtk proxy cargo build --release --locked --manifest-path benchmarks/vision-bindings/rust/Cargo.toml --target-dir benchmarks/vision-bindings/.runtime/rust-target
rtk proxy python benchmarks/vision-bindings/run.py
```

W pierwszym pomiarze wykorzystano już istniejące pakiety Pythona w podanych
wersjach; nie instalowano ich globalnie. Model (~14 MB) i JAR (~42 MB)
pobrano do `.runtime`. Cargo pobrało zależności do standardowego cache Cargo.
Modele, biblioteki, dane wejściowe i buildy nie trafiają do Gita.

Każda konfiguracja: 5 osobnych procesów, 1 pierwszy wynik, 20 rozgrzewek,
200 mierzonych wywołań. Badamy 1 i 4 wątki wewnętrzne silnika. Kolejność
języków rotuje; nie uruchamiamy benchmarków równolegle.
Raport powstaje jako `results-YYYY-MM-DD.json`; powtórzenie tego samego dnia
zastępuje ten plik. Zachowaj wcześniejszy raport, jeśli chcesz porównać serie.

`run.py` porównuje wszystkie 1000 liczb ostatniego wyniku każdego procesu
z wynikiem referencyjnym (`atol=rtol=1e-4`). Nie sprawdza jakości etykiet
ani zgodności każdego pośredniego wyniku. W pomiarze różnica wynosiła 0.

`process_to_first_result_ms` obejmuje start procesu, zależności, inicjalizację,
model, przygotowanie tensorów i pierwszy wynik. To świeży proces, ale nie
„zimny dysk”: system może mieć pliki w cache. Nie obejmuje pobrania/kompilacji.
`peak_working_set_mib` to maksymalny zaobserwowany Windows peak working set
procesu (próbkowanie 5 ms), nie sama sterta i nie pamięć GPU.

`load_ms` jest pomocniczym pomiarem wewnętrznym. Granice inicjalizacji środowiska
różnią się między API (Rust inicjalizuje część rzeczy leniwie), więc **nie używaj
tego pola do bezpośredniego rankingu startu języków**. Do tego jest wspólny
pomiar `process_to_first_result_ms`.

Wyniki nie obejmują dekodowania PNG/JPEG, przygotowania realnych zdjęć, OCR,
generowania zdań, HTTP/FastAPI/Springa, bazy ani kolejki wielu plików. To nie
JMH/pyperf ani formalna analiza statystyczna wszystkich języków.

Źródła: [model i licencja](https://github.com/onnx/models/tree/main/validated/vision/classification/mobilenet),
[wątki ONNX](https://onnxruntime.ai/docs/performance/tune-performance/threading.html),
[Java API](https://onnxruntime.ai/docs/get-started/with-java.html),
[ort](https://docs.rs/ort/2.0.0-rc.13/ort/).
