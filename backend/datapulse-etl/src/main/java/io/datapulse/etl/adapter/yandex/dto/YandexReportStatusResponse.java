package io.datapulse.etl.adapter.yandex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YandexReportStatusResponse(
    String status,
    String file,
    String generationRequestedAt,
    String generationFinishedAt
) {}
