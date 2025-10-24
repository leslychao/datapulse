package io.datapulse.marketplaces.wb;

import java.net.URI;
import java.time.LocalDate;
import java.util.function.Function;

public final class WbEndpoints {

  public static URI sales(String base, LocalDate from, LocalDate to) {
    return URI.create(base + "/sales" + "?dateFrom=" + from + "&dateTo=" + to);
  }
  public static URI stock(String base, LocalDate onDate) {
    return URI.create(base + "/stock" + "?date=" + onDate);
  }
  public static URI finance(String base, LocalDate from, LocalDate to) {
    return URI.create(base + "/finance" + "?dateFrom=" + from + "&dateTo=" + to);
  }
  public static URI reviews(String base, LocalDate from, LocalDate to) {
    return URI.create(base + "/reviews" + "?dateFrom=" + from + "&dateTo=" + to);
  }

  private WbEndpoints() {}
}
