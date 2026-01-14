package io.datapulse.core.repository.userprofile;

import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.BadRequestException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserProfileUpsertRepositoryImpl implements UserProfileUpsertRepository {

  private static final String UPSERT_RETURNING_ID_SQL = """
      with upsert as (
        insert into user_profile (
          keycloak_sub,
          email,
          full_name,
          username,
          created_at,
          updated_at
        )
        values (
          :keycloakSub,
          :email,
          :fullName,
          :username,
          now() at time zone 'utc',
          now() at time zone 'utc'
        )
        on conflict (keycloak_sub) do update set
          email = excluded.email,
          full_name = excluded.full_name,
          username = excluded.username,
          updated_at = now() at time zone 'utc'
        where
          (
            user_profile.email is distinct from excluded.email
            or user_profile.full_name is distinct from excluded.full_name
            or user_profile.username is distinct from excluded.username
          )
          and not exists (
            select 1
            from user_profile up2
            where lower(up2.email) = lower(excluded.email)
              and up2.keycloak_sub <> excluded.keycloak_sub
          )
        returning id
      )
      select id from upsert
      union all
      select up.id
      from user_profile up
      where up.keycloak_sub = :keycloakSub
      limit 1
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public long upsertAndSyncIfChangedSafeEmailReturningId(
      String keycloakSub,
      String email,
      String fullName,
      String username
  ) {
    Map<String, Object> params = Map.of(
        "keycloakSub", keycloakSub,
        "email", email,
        "fullName", fullName,
        "username", username
    );

    Long profileId = jdbcTemplate.queryForObject(UPSERT_RETURNING_ID_SQL, params, Long.class);
    if (profileId == null) {
      throw new BadRequestException(MessageCodes.USER_PROFILE_EMAIL_ALREADY_EXISTS, email);
    }
    return profileId;
  }
}
