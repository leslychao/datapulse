package io.datapulse.etl.dto;

import io.datapulse.etl.MarketplaceEvent;
import java.time.LocalDate;

/**
 * Внутренняя доменная команда запуска ETL-ивента.
 *
 * Этот объект:
 *  - создаётся один раз при старте,
 *  - пересылается в Rabbit при WAIT/Retry,
 *  - несёт ядро контекста Event-запуска,
 *  - не зависит от HTTP-контракта (EtlRunRequest),
 *  - не зависит от ingest/execution-логики.
 *
 * Используется только оркестратором.
 */
public record OrchestrationCommand(
    String requestId,
    Long accountId,
    MarketplaceEvent event,
    LocalDate dateFrom,
    LocalDate dateTo
) {}
