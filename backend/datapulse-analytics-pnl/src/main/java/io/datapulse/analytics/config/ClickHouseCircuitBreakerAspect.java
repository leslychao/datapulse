package io.datapulse.analytics.config;

import io.datapulse.analytics.domain.AnalyticsUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ClickHouseCircuitBreakerAspect {

  private final CircuitBreaker analyticsClickhouseCircuitBreaker;

  @Around("execution(public * io.datapulse.analytics.persistence.PnlReadRepository.*(..)) || "
      + "execution(public * io.datapulse.analytics.persistence.InventoryReadRepository.*(..)) || "
      + "execution(public * io.datapulse.analytics.persistence.ReturnsReadRepository.*(..)) || "
      + "execution(public * io.datapulse.analytics.persistence.DataQualityReadRepository.*(..))")
  public Object wrapWithCircuitBreaker(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
      return analyticsClickhouseCircuitBreaker.executeCheckedSupplier(() -> {
        try {
          return joinPoint.proceed();
        } catch (Throwable t) {
          if (t instanceof Exception ex) {
            throw ex;
          }
          throw new RuntimeException(t);
        }
      });
    } catch (CallNotPermittedException e) {
      log.warn("ClickHouse circuit breaker OPEN, rejecting call: method={}",
          joinPoint.getSignature().toShortString());
      throw new AnalyticsUnavailableException();
    }
  }
}
