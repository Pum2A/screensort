# Docelowa analiza kontekstowa — uzgodniony kierunek, nie gotowa funkcja

Stan: 2026-10-20. Obecny generator wybiera słowa z caption. Nie przedstawiamy go
jako semantycznego nazywania zdjęć. Poniżej plan dla Java/Python/Rust/React.

## Przepływ produktu

```text
Wybór folderu → skan i zapis katalogu → Analizuj wszystko
  → odczyt obrazu i kontrola aktualnego SHA
  → lokalne OCR + obserwacje wizualne
  → wspólna interpretacja + ocena niejednoznaczności / prywatności
  → krótka nazwa + kategoria + alternatywy, jeśli potrzebne
  → przegląd / poprawki / zatwierdzenie użytkownika
  → podgląd planu folderów i kolizji nazw
  → zatwierdzone, zweryfikowane kopiowanie do folderu wynikowego
```

Nie zmieniamy nazw i nie usuwamy oryginałów w trakcie analizy. Foldery proponowane
w UI nie są jeszcze folderami utworzonymi na dysku.

## OCR i wizja mają korzystać ze wspólnego kontekstu — bez pętli bez końca

OCR odczytuje znaki. Wizja opisuje wygląd i scenę. Dopiero osobny etap interpretacji
otrzymuje obraz/obserwacje, tekst OCR i opcjonalny kontekst użytkownika, np.
„screenshoty z gier”. Nie trzeba zmieniać OCR w model rozumiejący całą scenę.
Nie wysyłamy też wyników naprzemiennie w nieskończoność: podstawowy przebieg jest
jeden, a ewentualne dodatkowe rozpoznanie fragmentu ma jawny limit.

Brak wykrytego tekstu jest prawidłowym wynikiem OCR. Wtedy podstawą jest obraz,
nie wymyślony tekst. Awaria OCR to inny stan niż poprawnie odczytane zero znaków.
Przy awarii wizji dobry tekst może uzasadniać propozycję, ale wynik musi zachować
informację, że analizy wizualnej nie udało się wykonać.

Wynik interpretacji powinien rozdzielać:

| Pole / znaczenie | Przykład |
| --- | --- |
| Rodzaj materiału | screenshot gry / fotografia jedzenia / dokument |
| Najważniejszy temat | postacie w lobby, nie kolor włosów w tle |
| Przesłanki | rozpoznany napis, element interfejsu, obiekt |
| Proponowana krótka nazwa | `roblox_postacie_w_lobby.png`, tylko przy dostatecznych przesłankach |
| Kategoria | Gry / Jedzenie / Dokumenty |
| Alternatywy | do 3 sensownych interpretacji przy niepewności |
| Powód przeglądu | nieczytelny tekst, sprzeczne przesłanki, możliwe dane wrażliwe |
| Wersja silnika / reguł | do unieważniania starego wyniku i cache |

Nie przydzielamy sztucznego „98% pewności”. Caption „cartoon character” sam w sobie
nie wystarcza do stwierdzenia „Roblox”. Nie da się zagwarantować, że każda
wstępna analiza definitywnie rozpozna grę, jedzenie lub dokument.

## Odpowiedzialność komponentów

**Python:** rozpoznanie obrazu i tekstu oraz przyszły interpretator. Nowy wynik
jest wersjonowanym kontraktem, niezależnym od klas JPA. Modele ładowane raz,
ograniczona liczba pracujących zadań. Nie pobieramy teraz dodatkowego dużego modelu.

**Java:** weryfikacja źródła i odpowiedzi, zadania folderowe, checkpointy, zapis
interpretacji, walidacja nazw, decyzje użytkownika oraz plan organizacji.
Stare decyzje użytkownika nie mogą zostać po cichu nadpisane nową propozycją modelu.

**Rust:** nadzorowanie procesów, osobne etapy startu i zakończenia, ponowna próba
bez restartu całego katalogu, pobieranie zweryfikowanych pakietów na żądanie.
Żaden broker nie zastąpi poprawnej obsługi zawieszonego procesu modelu.

**React:** przyjazne etapy startu, sensowne stany przycisków, jawne wznowienie
przerwanego zadania, przegląd propozycji, drzewo kategorii i panel wybranego zdjęcia.
Animacje pokazują zmianę stanu, respektują reduced motion i nie blokują pracy.

## Prywatność i rozmiar modeli

Pobranie plików modelu z internetu nie oznacza wysłania zdjęć na zewnątrz.
Obecne modele uruchamiamy lokalnie; zdjęcia przechodzą tylko do lokalnego procesu
Python. Wywołanie zewnętrznego API chmurowego byłoby innym trybem i wymagałoby
osobnej, świadomej zgody. Nie dodajemy go domyślnie ani jako ukryty fallback.

Podejrzenie dokumentu wrażliwego ma wstrzymywać automatyczną organizację i oznaczać
plik do ręcznego przeglądu, a nie kasować plik. Dane dokumentu nie powinny trafiać
do automatycznej nazwy. Klasyfikator nie daje gwarancji wykrycia wszystkich
prywatnych danych, dlatego nie używamy go jako jedynej bariery przed chmurą.

Profil lekki/dokładny wybierzemy po pomiarach na CPU: rozmiar pobrania i na dysku,
zimny/ciepły start, RAM, czas na obraz i jakość propozycji. Mocniejszy model nie
jest automatycznie dobrym domyślnym wyborem. Ewentualna hybryda najpierw próbuje
lekkiego przebiegu, a droższy uruchamia przy niejednoznaczności i zgodzie użytkownika.

## Kolejność wdrażania i warunki zakończenia

### Teraz: metryki i opcjonalny cache

Osobny zakres opisany w `review-observability-and-cache.md`. Redis przyspiesza
ponowny odczyt podglądów, nie sam model i nie jakość nazewnictwa. Prometheus
pomaga znaleźć koszt; nie jest optymalizacją sam w sobie.

### Następnie: niezawodne uruchamianie analizy

Rozdzielić importy, ładowanie OCR, ładowanie wizji i próbny przebieg. Pokazywać
rzeczywisty etap/czas zamiast jednego niekończącego się loadera. Po timeout/błędzie
poprawnie zakończyć wyłącznie własny proces analizy, umożliwić ponowny start,
zachować katalog i checkpointy. Sprawdzić restart przy 7/94 oraz wznowienie bez
dublowania już zapisanych wyników. Nie zwiększać timeoutów bez pomiaru.

### Potem: jakość wspólnej interpretacji

Przygotować mały zestaw ocenianych przykładów: gry/Roblox, jedzenie, kod, dokumenty,
obrazy bez tekstu i niejednoznaczne. Prywatnych zdjęć i prawdziwych dokumentów
nie commitujemy; dokumenty testowe powinny być syntetyczne. Porównać obecny caption
z kandydatami interpretacji. Mierzyć trafność rodzaju/tematu, użyteczność nazwy,
nieuzasadnione rozpoznania marek, top-3 przy niepewności oraz czas/RAM. Dopiero
wtedy wybrać model i ewentualny mały, udokumentowany trening. Zielone testy
kontraktu JSON nie zastępują oceny jakości na zdjęciach.

### UI i organizacja

Zbudować drzewo kategorii + płótno zdjęć + panel przeglądu. Przycisk „Analizuj
wszystko” korzysta z istniejących trwałych zadań. Stan wrażliwy/niepewny nie może
przejść do automatycznego zatwierdzenia. Po zatwierdzeniu: plan kopii, kontrola
kolizji i miejsca, zweryfikowane kopiowanie bez nadpisywania. Brak ukrytego
przenoszenia, usuwania czy zmiany oryginałów.

Kafka, RabbitMQ, NGINX i Kubernetes pozostają poza tym zakresem. Nie ma obecnie
uzasadnionej potrzeby dokładania tych usług do lokalnego instalatora.
