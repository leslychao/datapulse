package io.datapulse.core.repository;

import io.datapulse.core.entity.account.AccountMemberEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountMemberRepository extends JpaRepository<AccountMemberEntity, Long> {

  boolean existsByAccountIdAndUserId(Long accountId, Long userId);

  Optional<AccountMemberEntity> findByAccountIdAndUserId(Long accountId, Long userId);

  List<AccountMemberEntity> findAllByAccountId(Long accountId);

  List<AccountMemberEntity> findAllByUserId(Long userId);
}
