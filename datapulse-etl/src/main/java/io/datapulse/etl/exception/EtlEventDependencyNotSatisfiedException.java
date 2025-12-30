package io.datapulse.etl.exception;

import static io.datapulse.domain.MessageCodes.ETL_EVENT_DEPENDENCY_NOT_SATISFIED;

import io.datapulse.domain.exception.AppException;
import org.springframework.http.HttpStatus;

public class EtlEventDependencyNotSatisfiedException extends AppException {

  public EtlEventDependencyNotSatisfiedException(Object... args) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, ETL_EVENT_DEPENDENCY_NOT_SATISFIED, args);
  }
}
