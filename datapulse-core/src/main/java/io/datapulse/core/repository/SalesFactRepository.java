package io.datapulse.core.repository;

import io.datapulse.core.entity.SalesFactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesFactRepository extends JpaRepository<SalesFactEntity, Long> {

}
