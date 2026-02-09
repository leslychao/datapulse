package io.datapulse.core.repository;

import io.datapulse.core.entity.AccountMemberEntity;
import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountMemberRepository extends JpaRepository<AccountMemberEntity, Long> {

  Optional<AccountMemberEntity> findByAccount_IdAndUser_Id(Long accountId, Long userId);

  Optional<AccountMemberEntity> findByAccount_IdAndUser_IdAndStatus(
      Long accountId,
      Long userId,
      AccountMemberStatus status
  );

  List<AccountMemberEntity> findAllByAccount_Id(Long accountId);

  List<AccountMemberEntity> findAllByAccount_IdOrderByIdAsc(Long accountId);

  @Query("""
      select am
      from AccountMemberEntity am
      join fetch am.user u
      where am.account.id = :accountId
      order by am.id asc
      """)
  List<AccountMemberEntity> findAllByAccountIdWithUserOrderByIdAsc(
      @Param("accountId") Long accountId);

  List<AccountMemberEntity> findAllByUser_Id(Long userId);

  Optional<AccountMemberEntity> findByIdAndAccount_Id(Long memberId, Long accountId);

  @Query(
      value = """
          select am
          from AccountMemberEntity am
          where am.account.id = :accountId
            and am.role = :role
            and am.status = :status
          order by am.id asc
          """
  )
  List<AccountMemberEntity> findActiveOwners(
      @Param("accountId") Long accountId,
      @Param("role") AccountMemberRole role,
      @Param("status") AccountMemberStatus status
  );
}
