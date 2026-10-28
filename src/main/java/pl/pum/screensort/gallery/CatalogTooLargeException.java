package pl.pum.screensort.gallery;

import java.io.IOException;

/** Katalog ma więcej obrazów, niż pozwala pojedynczy odczyt bez paginacji. */
public class CatalogTooLargeException extends IOException {

  public CatalogTooLargeException(String message) {
    super(message);
  }
}
