package io.datapulse.execution.api;

public record PriceActionKpiResponse(long total, long pending, long executing, long failed) {}
