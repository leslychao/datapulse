package io.datapulse.core.repository.userprofile;

public interface UserProfileUpsertRepository {

  long upsertAndSyncIfChangedSafeEmailReturningId(
      String keycloakSub,
      String email,
      String fullName,
      String username
  );
}
