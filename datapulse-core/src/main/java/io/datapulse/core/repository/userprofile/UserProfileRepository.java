package io.datapulse.core.repository.userprofile;

import io.datapulse.core.entity.userprofile.UserProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {

  Optional<UserProfileEntity> findByKeycloakSub(String keycloakSub);

  boolean existsByKeycloakSub(String keycloakSub);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

  @Modifying
  @Query(value = """
      insert into user_profile (
        keycloak_sub,
        email,
        full_name,
        username,
        created_at,
        updated_at
      )
      select
        :keycloakSub,
        :email,
        :fullName,
        :username,
        now() at time zone 'utc',
        now() at time zone 'utc'
      where not exists (
        select 1
        from user_profile up
        where lower(up.email) = lower(:email)
          and up.keycloak_sub <> :keycloakSub
      )
      on conflict (keycloak_sub) do update set
        email = excluded.email,
        full_name = excluded.full_name,
        username = excluded.username,
        updated_at = now() at time zone 'utc'
      where
        user_profile.email is distinct from excluded.email
        or user_profile.full_name is distinct from excluded.full_name
        or user_profile.username is distinct from excluded.username
      """, nativeQuery = true)
  int upsertAndSyncIfChangedSafeEmail(
      @Param("keycloakSub") String keycloakSub,
      @Param("email") String email,
      @Param("fullName") String fullName,
      @Param("username") String username
  );
}
