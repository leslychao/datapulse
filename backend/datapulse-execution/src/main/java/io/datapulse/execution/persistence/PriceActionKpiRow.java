package io.datapulse.execution.persistence;

public record PriceActionKpiRow(long total, long pending, long executing, long failed) {}
