package io.datapulse.core.exception;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

@Getter
public class HttpErrorException extends RuntimeException {
  private final HttpStatusCode statusCode;
  private final HttpHeaders headers;
  private final String responseBody;

  public HttpErrorException(HttpStatusCode statusCode, HttpHeaders headers, String responseBody) {
    super("HTTP ошибка: " + statusCode.value() + (responseBody == null || responseBody.isBlank() ? "" : " — " + truncate(responseBody)));
    this.statusCode = statusCode;
    this.headers = headers;
    this.responseBody = responseBody;
  }

  private static String truncate(String s) { return s.length() <= 200 ? s : s.substring(0, 200) + "…"; }
}
