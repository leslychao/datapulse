package io.datapulse.core.repository;

import io.datapulse.core.entity.AccountEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

  Optional<AccountEntity> findByName(String name);

  boolean existsByName(String name);
}
