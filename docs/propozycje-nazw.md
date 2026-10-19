# Propozycja nazwy: od analizy do decyzji

To pierwszy krok organizowania zdjęć. Możesz poprawić i zatwierdzić nazwę dla
prawdziwego rekordu katalogu. Zapisujemy ją w bazie, ale **nie zmieniamy pliku**.
Ta funkcja jest w bieżących źródłach. Wcześniej zbudowany instalator 0.1.1 jej
nie zawiera; nie podmieniamy automatycznie plików zainstalowanej aplikacji.

## Jak to sprawdzić w aplikacji

1. Uruchom aktualny backend i frontend w swoim dotychczasowym trybie developerskim.
   Flyway doda tabelę przez nową migrację V5; V1–V4 nie zostały zmienione.
2. Otwórz katalog i wybierz miniaturę. Korzystaj z panelu prawdziwego zdjęcia,
   nie z przycisku „Analiza · DEMO”.
3. Jeśli zdjęcie nie ma analizy, uruchom „Analizuj obraz” z opisem wizualnym.
   Dla istniejącej analizy nie trzeba ponownie uruchamiać modeli.
4. Pod wynikiem zobaczysz „Nadaj obrazowi nazwę”. Popraw pole i zatwierdź.
5. Wybierz inny obraz, wróć albo odśwież aplikację. Zatwierdzona nazwa jest
   odczytywana z PostgreSQL, a nie tylko pamiętana w stanie Reacta.
6. Oryginalna nazwa w galerii i na dysku pozostaje taka sama. Potwierdzenie mówi
   o zapisanej decyzji, nie o wykonanej operacji na pliku.

## Przepływ

```text
Zapisany opis Florence + oryginalna nazwa
  → GET /api/catalog/files/{fileId}/naming
  → NamingService odczytuje katalog, analizę i wcześniejszą decyzję
  → NameSuggestionGenerator tworzy propozycję
  → NamingPanel pokazuje pole do edycji

Zatwierdzenie użytkownika
  → PUT /api/catalog/files/{fileId}/naming
  → kontrola analizy, hasha, nazwy i wersji decyzji
  → zapis lub aktualizacja file_naming
  → odpowiedź do Reacta z nazwą i datą zatwierdzenia
```

Tauri przepuszcza wyłącznie te konkretne trasy GET/PUT przez istniejący most.
Nie dodano ogólnego dostępu do plików ani kolejnego serwisu.

## Co robi generator, a czego nie robi

Pierwsza wersja wybiera do sześciu słów opisu, pomija m.in. angielskie rodzajniki
i część słów łączących, upraszcza zapis i łączy słowa podkreśleniami.
Zachowuje ostatnie rozszerzenie oryginału.

```text
IMG_0026.png
„A mug on a desk next to a computer monitor.”
→ mug_desk_computer_monitor.png
→ Twoja poprawka: kubek_na_biurku.png
```

To reguła tekstowa, nie model językowy. Nie tłumaczy opisu, nie gwarantuje
trafnego streszczenia i nie dopisuje kategorii. Słowa mogą być niedoskonałe,
dlatego pole jest edytowalne. Generator normalizuje zapis łaciński; dla opisu
bez odpowiednich słów może nie dać propozycji. Możesz wtedy wpisać własną nazwę.
Przy analizie tylko OCR również dostępny jest zapis własnej nazwy, ale nie
udajemy, że z tekstu OCR powstał opis sceny.

## Gdzie jest kod

| Plik / miejsce | Zadanie |
| --- | --- |
| `naming/NameSuggestionGenerator.java` | Sama reguła opis → propozycja; bez odczytywania zdjęcia i bazy. |
| `naming/FileNameRules.java` | Sprawdza pojedynczą nazwę i zachowanie rozszerzenia. |
| `naming/NamingService.java` | Łączy odczyt analizy z generowaniem i zapisem decyzji. Tu jest granica transakcji. |
| `naming/api/NamingController.java` | Dwa wejścia HTTP: odczyt formularza i zatwierdzenie. |
| `naming/api/dto/NamingReviewDto.java` | Dane formularza, nie encja JPA ani instrukcja zmiany pliku. |
| `naming/database/` | Encja i repozytorium zatwierdzonej decyzji. |
| `V5__create_file_naming.sql` | Nowa tabela, klucze obce, jedna decyzja na rekord pliku. |
| `catalog/database/FileRecordRepository.java` | Dodany odczyt z blokadą na czas krótkiego zatwierdzenia. |
| `frontend/src/naming/` | Osobny formularz, typy, wywołania API, wskazówki walidacji i CSS Module. |
| `frontend/src/analysis/AnalysisPanel.tsx` | Wyświetla nowy formularz pod zapisaną analizą. |
| `frontend/src-tauri/src/desktop/mod.rs` | Ograniczona lista dozwolonych tras rozszerzona o naming. |

## Dlaczego zapis jest osobno

`file_record.original_name` opisuje rzeczywistą nazwę z ostatniego skanu.
`file_naming.approved_name` to Twoja decyzja o przyszłej nazwie. Zastąpienie
pierwszego pola przed operacją na dysku sprawiłoby, że katalog opisywałby plik,
który jeszcze nie istnieje pod taką nazwą.

Jeden rekord decyzji przechowuje plik, ID analizy, hash obrazu, zatwierdzoną
nazwę, czas i numer wersji. Kolejne zatwierdzenie aktualizuje go, a nie tworzy
następną kopię. To nie jest jeszcze historia wszystkich decyzji użytkownika.

## Po co hash i revision w formularzu

Hash wiąże decyzję z zawartością obrazu znaną katalogowi. Zmiana hasha w skanie
unieważnia poprzednią decyzję dla aktualnej zawartości, ale jej nie usuwa.
Nie jest to ponowne sprawdzenie dysku: do przyszłej operacji kopiowania potrzebna
będzie świeża kontrola rzeczywistego źródła.

`revision` to licznik zmian decyzji. `-1` w DTO oznacza brak wcześniejszego
zatwierdzenia; pierwszy zapis otrzymuje `0`. Jeżeli w dwóch oknach otworzysz
ten sam formularz, pierwsze może zapisać decyzję. Drugie dostaje konflikt,
zamiast po cichu nadpisać pierwszy zapis. W UI można jawnie wczytać aktualną decyzję.

Krótka blokada wiersza pliku porządkuje równoczesne pierwsze zapisy, gdy wiersz
decyzji jeszcze nie istnieje. `@Version` odpowiada za wersjonowanie późniejszych
zmian. Blokady nie obejmują uruchamiania OCR, Florence ani skanowania folderu.

## Ograniczenia i bezpieczeństwo

- Java ponownie sprawdza nazwę; walidacja Reacta to tylko szybsza informacja dla użytkownika.
- Odrzucane są ścieżki, znaki sterujące/formatujące, zarezerwowane nazwy Windows,
  nieprawidłowe końcówki i nadmierna długość. Limit 255 bajtów UTF-8 jest ostrożną
  zasadą dla przyszłych operacji między systemami, nie limitem tekstu w PostgreSQL.
- Analiza niezgodna z hashem katalogu blokuje zatwierdzenie. Zmienione dane
  formularza lub nowsza decyzja zwracają konflikt HTTP 409.
- Nie sprawdzamy jeszcze kolizji nazw w folderze wyjściowym — nie ma operacji zapisu
  plików. Zatwierdzenie nie daje prawa do nadpisania jakiegokolwiek pliku.
- Kategorie, tagi, analiza całego folderu i wykonywanie kopii pozostają poza tym etapem.
- Załączniki rozmowy zostały wykluczone z Gita. Nie zostały skasowane ani dodane
  do repozytorium. Nie jest to wykonanie pozostałych poprawek z audytu publikacji.

## Sprawdzenie zmian

Nowe testy Javy obejmują reguły nazw, generator, usługę, API i zapis w PostgreSQL.
Test bazy sprawdza ponowny odczyt, aktualizację, dwa równoczesne zatwierdzenia,
nieaktualną decyzję i brak zmiany źródłowego pliku. Dane testowe są syntetyczne.

Domyślnie `NamingJpaTest` używa własnego kontenera PostgreSQL 18.6. Dopuszcza też
jawnie wskazaną, izolowaną bazę testową na loopback o nazwie
`screensort_naming_test_<hex>`; nie używa konfiguracji bazy aplikacji jako fallbacku.
W tym środowisku Docker był niedostępny, więc cztery testy zapisu wykonano na
nowym klastrze dołączonego PostgreSQL, zatrzymanym po teście.

Komendy zwykłych testów:

```powershell
.\mvnw.cmd -B test "-Dtest=!*JpaTest,!*IntegrationTest"
.\mvnw.cmd -B test "-Dtest=NamingJpaTest"
```

Druga komenda wymaga działającego Dockera, jeżeli nie skonfigurowano izolowanego
wariantu natywnego. Dodatkowo wykonujemy testy i build frontendu oraz test mostu
Tauri. Test formularza w przeglądarce używa kontrolowanych odpowiedzi API;
nie jest dowodem jakości Florence ani testem całego instalatora.

Weryfikacja z 18–19.10.2026:

| Zakres | Wynik |
| --- | --- |
| Java bez `*JpaTest` i `*IntegrationTest` | 129 zgłoszonych, 127 zaliczonych, 2 pominięte; brak błędów. |
| Nowy `NamingJpaTest`, izolowany natywny PostgreSQL | 4/4; migracja V5, zapis/odczyt, wersja decyzji i współbieżność. |
| Frontend | 37/37; lint bez ostrzeżeń, produkcyjny build poprawny. |
| Rust | 6/6; kontrola formatowania poprawna. |
| Formularz w przeglądarce | Edycja i zapis, odczyt po odświeżeniu, reset przy zmianie zdjęcia, konflikt i jawne odświeżenie decyzji; widok 390 px bez poziomego przewijania. |

Test przeglądarkowy użył produkcyjnego buildu i syntetycznych odpowiedzi API;
trwały zapis sprawdzono oddzielnie na rzeczywistym PostgreSQL. Nie uruchamiano
ponownie pełnego zestawu istniejących integracji Dockerowych ani nie budowano
nowego instalatora. Logi i zrzuty weryfikacyjne pozostały lokalnie w `.release/`.

Dokumentacja użyta do implementacji:
[transakcje Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html),
[blokady repozytorium](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html),
[reset stanu formularza przez key](https://react.dev/learn/preserving-and-resetting-state).
