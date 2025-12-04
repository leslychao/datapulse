package io.datapulse.etl.flow.core.model;

import java.time.LocalDate;

public record EventWindow(LocalDate from, LocalDate to) {
}
