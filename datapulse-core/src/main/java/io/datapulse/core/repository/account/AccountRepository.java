package io.datapulse.core.repository.account;

import io.datapulse.core.entity.account.AccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

  Optional<AccountEntity> findByActiveIsTrueAndNameIgnoreCase(String name);

  @Query("""
      select a
      from AccountEntity a
        join AccountMemberEntity am on am.account.id = a.id
      where
        am.user.id = :profileId
        and am.status = io.datapulse.domain.AccountMemberStatus.ACTIVE
      """)
  List<AccountEntity> findAccessibleActiveAccountsForProfileId(@Param("profileId") long profileId);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

  List<AccountEntity> findAllByActiveIsTrue();
}
