package io.datapulse.persistence.repo;

import io.datapulse.persistence.entity.AccountEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

  List<AccountEntity> findByActiveTrue();
}
