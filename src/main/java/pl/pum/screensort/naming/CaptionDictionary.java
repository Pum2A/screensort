package pl.pum.screensort.naming;

import java.util.Map;
import java.util.Optional;

/**
 * Polish words for the English vocabulary the caption model uses on screenshots. Names are written
 * without diacritics, like the rest of the generator. A word missing here is left out of a Polish
 * name rather than mixed in as English.
 */
final class CaptionDictionary {
  private static final Map<String, String> POLISH = Map.ofEntries(
      // What is on the screen
      Map.entry("screenshot", "zrzut"), Map.entry("screen", "ekran"), Map.entry("computer", "komputer"),
      Map.entry("monitor", "monitor"), Map.entry("display", "ekran"), Map.entry("laptop", "laptop"),
      Map.entry("phone", "telefon"), Map.entry("cellphone", "telefon"), Map.entry("smartphone", "telefon"),
      Map.entry("tablet", "tablet"), Map.entry("keyboard", "klawiatura"), Map.entry("mouse", "mysz"),
      Map.entry("website", "strona"), Map.entry("page", "strona"), Map.entry("web", "internet"),
      Map.entry("window", "okno"), Map.entry("app", "aplikacja"), Map.entry("application", "aplikacja"),
      Map.entry("program", "program"), Map.entry("software", "program"), Map.entry("code", "kod"),
      Map.entry("text", "tekst"), Map.entry("words", "tekst"), Map.entry("writing", "tekst"),
      Map.entry("letter", "list"), Map.entry("document", "dokument"), Map.entry("paper", "kartka"),
      Map.entry("sheet", "arkusz"), Map.entry("spreadsheet", "arkusz"), Map.entry("table", "tabela"),
      Map.entry("chart", "wykres"), Map.entry("graph", "wykres"), Map.entry("diagram", "diagram"),
      Map.entry("map", "mapa"), Map.entry("list", "lista"), Map.entry("menu", "menu"),
      Map.entry("receipt", "paragon"), Map.entry("invoice", "faktura"), Map.entry("ticket", "bilet"),
      Map.entry("form", "formularz"), Map.entry("email", "email"), Map.entry("message", "wiadomosc"),
      Map.entry("chat", "czat"), Map.entry("conversation", "rozmowa"), Map.entry("calendar", "kalendarz"),
      Map.entry("logo", "logo"), Map.entry("icon", "ikona"), Map.entry("icons", "ikony"),
      Map.entry("button", "przycisk"), Map.entry("video", "wideo"), Map.entry("game", "gra"),
      Map.entry("games", "gry"), Map.entry("character", "postac"), Map.entry("cartoon", "kreskowka"),
      Map.entry("drawing", "rysunek"), Map.entry("painting", "obraz"), Map.entry("poster", "plakat"),
      Map.entry("book", "ksiazka"), Map.entry("card", "karta"), Map.entry("sign", "znak"),
      Map.entry("meme", "mem"), Map.entry("photo", "zdjecie"), Map.entry("selfie", "selfie"),
      // People and animals
      Map.entry("man", "mezczyzna"), Map.entry("men", "mezczyzni"), Map.entry("woman", "kobieta"),
      Map.entry("women", "kobiety"), Map.entry("person", "osoba"), Map.entry("people", "ludzie"),
      Map.entry("child", "dziecko"), Map.entry("boy", "chlopiec"), Map.entry("girl", "dziewczyna"),
      Map.entry("baby", "dziecko"), Map.entry("group", "grupa"), Map.entry("face", "twarz"),
      Map.entry("hand", "dlon"), Map.entry("dog", "pies"), Map.entry("cat", "kot"),
      Map.entry("bird", "ptak"), Map.entry("horse", "kon"), Map.entry("fish", "ryba"),
      // Things and places
      Map.entry("car", "samochod"), Map.entry("bus", "autobus"), Map.entry("train", "pociag"),
      Map.entry("bike", "rower"), Map.entry("bicycle", "rower"), Map.entry("plane", "samolot"),
      Map.entry("airplane", "samolot"), Map.entry("boat", "lodz"), Map.entry("house", "dom"),
      Map.entry("building", "budynek"), Map.entry("room", "pokoj"), Map.entry("kitchen", "kuchnia"),
      Map.entry("office", "biuro"), Map.entry("desk", "biurko"), Map.entry("chair", "krzeslo"),
      Map.entry("bed", "lozko"), Map.entry("door", "drzwi"), Map.entry("city", "miasto"),
      Map.entry("street", "ulica"), Map.entry("road", "droga"), Map.entry("park", "park"),
      Map.entry("beach", "plaza"), Map.entry("sea", "morze"), Map.entry("ocean", "ocean"),
      Map.entry("water", "woda"), Map.entry("mountain", "gora"), Map.entry("mountains", "gory"),
      Map.entry("forest", "las"), Map.entry("tree", "drzewo"), Map.entry("trees", "drzewa"),
      Map.entry("flower", "kwiat"), Map.entry("flowers", "kwiaty"), Map.entry("grass", "trawa"),
      Map.entry("sky", "niebo"), Map.entry("sun", "slonce"), Map.entry("snow", "snieg"),
      Map.entry("night", "noc"), Map.entry("food", "jedzenie"), Map.entry("pizza", "pizza"),
      Map.entry("cake", "ciasto"), Map.entry("plate", "talerz"), Map.entry("cup", "kubek"),
      Map.entry("mug", "kubek"), Map.entry("bottle", "butelka"), Map.entry("coffee", "kawa"),
      Map.entry("shirt", "koszulka"), Map.entry("shoes", "buty"), Map.entry("bag", "torba"),
      Map.entry("box", "pudelko"), Map.entry("ball", "pilka"), Map.entry("toy", "zabawka"),
      // Colours and descriptions
      Map.entry("white", "bialy"), Map.entry("black", "czarny"), Map.entry("red", "czerwony"),
      Map.entry("blue", "niebieski"), Map.entry("green", "zielony"), Map.entry("yellow", "zolty"),
      Map.entry("orange", "pomaranczowy"), Map.entry("purple", "fioletowy"), Map.entry("pink", "rozowy"),
      Map.entry("brown", "brazowy"), Map.entry("grey", "szary"), Map.entry("gray", "szary"),
      Map.entry("dark", "ciemny"), Map.entry("bright", "jasny"), Map.entry("small", "maly"),
      Map.entry("large", "duzy"), Map.entry("big", "duzy"), Map.entry("old", "stary"),
      Map.entry("new", "nowy"), Map.entry("background", "tlo"), Map.entry("blurry", "rozmyty"));

  private CaptionDictionary() {}

  static Optional<String> polish(String englishWord) {
    return Optional.ofNullable(POLISH.get(englishWord));
  }
}
