package io.datapulse.core.web;
    
    import lombok.Value;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.validation.BindException;
    import org.springframework.web.bind.MethodArgumentNotValidException;
    import org.springframework.web.bind.annotation.ControllerAdvice;
    import org.springframework.web.bind.annotation.ExceptionHandler;
    
    @Slf4j
    @ControllerAdvice
    public class GlobalExceptionHandler {
    
      @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
      public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        log.warn("Ошибка валидации: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("Неверные параметры запроса"));
      }
    
      @ExceptionHandler(Exception.class)
      public ResponseEntity<ErrorResponse> handleOther(Exception ex) {
        log.error("Внутренняя ошибка", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Внутренняя ошибка сервера"));
      }
    
      @Value
      public static class ErrorResponse {
        String message;
      }
    }
