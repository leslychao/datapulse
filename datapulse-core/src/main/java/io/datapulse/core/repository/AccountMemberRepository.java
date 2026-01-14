package io.datapulse.core.repository;

import io.datapulse.core.entity.account.AccountMemberEntity;
import io.datapulse.domain.AccountMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountMemberRepository extends JpaRepository<AccountMemberEntity, Long> {

  boolean existsByAccount_IdAndUser_Id(Long accountId, Long userId);

  Optional<AccountMemberEntity> findByAccount_IdAndUser_Id(Long accountId, Long userId);

  Optional<AccountMemberEntity> findByAccount_IdAndUser_IdAndStatus(
      Long accountId,
      Long userId,
      AccountMemberStatus status
  );

  List<AccountMemberEntity> findAllByAccount_Id(Long accountId);

  List<AccountMemberEntity> findAllByUser_Id(Long userId);
}
