package io.datapulse.etl.domain.repository;

import io.datapulse.etl.domain.entity.Execution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository {

  Execution save(Execution execution);

  Execution update(Execution execution);

  Optional<Execution> findById(UUID id);

  List<Execution> findByEventId(UUID eventId);
}
