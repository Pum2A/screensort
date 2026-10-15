# ADR-005: prywatne runtime'y w instalatorze Windows

Data: 2026-10-15. Status: przyjęte na wyraźny wybór właściciela.

## Problem

Użytkownik chce uruchomić zainstalowaną aplikację bez Dockera, JDK, Node i Pythona.
Zachowujemy Javę jako rdzeń i PostgreSQL jako trwały katalog oraz osobny lokalny
proces analizy Python (ADR-004). Samo spakowanie Reacta w Tauri nie zapewnia tego.

## Decyzja

Dołączamy prywatne runtime'y Windows x64: obraz Java z jlink, CPython embedded,
zależności i modele CPU oraz serwer PostgreSQL. Rust nadzoruje ich start/stop,
losuje porty/tokeny, sprawdza gotowość. Windows Job Object domyka własne procesy
w razie śmierci rodzica. PostgreSQL nie jest usługą systemową ani kontenerem.

Desktop używa natywnego mostu ograniczonego do API produktu. React nie dostaje
portów i sekretów. Java ma osobny profil desktop z loopback i tokenem sesji,
Python przyjmuje tylko ograniczone bajty, nigdy ścieżkę od webview.

## Alternatywy

* Wymaganie Docker Desktop: wygodne developersko, odrzucone dla użytkownika.
* Zdalna baza/analiza: zmienia prywatność, koszty i wymaga sieci; poza zakresem.
* SQLite/H2 dla desktop: mniejszy pakiet, ale wymaga osobnej decyzji o SQL,
  migracjach i semantyce; nie podmieniamy teraz bazy przy okazji pakowania.
* Globalna instalacja Pythona/Javy lub usługi PostgreSQL: konflikty wersji,
  uprawnienia i trudniejsze sprzątanie; wybieramy procesy prywatne dla aplikacji.

## Konsekwencje

Pakiet jest duży; trzeba aktualizować i audytować dołączone runtime'y, biblioteki
oraz licencje. Bazę trzymamy poza instalacją i nie usuwamy przy przebudowie pakietu.
Baza desktop jest oddzielna od Dockera; migracja danych użytkownika wymaga
osobnego świadomego narzędzia. Wersja przedpremierowa nie jest podpisana;
przed publiczną dystrybucją potrzebne są próby na czystym Windows, przegląd licencji,
podpisywanie, aktualizacje i pełny audyt bezpieczeństwa.

Szczegóły przepływu i budowania: [instalator Windows](../instalator-windows.md).
