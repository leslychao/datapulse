package io.datapulse.iam.authorization;

import io.datapulse.domain.AccountMemberRole;

public final class AccountRoleCapabilities {

  private AccountRoleCapabilities() {
  }

  public static boolean canRead(AccountMemberRole role) {
    return role != null;
  }

  public static boolean canWrite(AccountMemberRole role) {
    return role != null && AccountMemberRole.writeRoles().contains(role);
  }

  public static boolean canManageMembers(AccountMemberRole role) {
    return role != null && AccountMemberRole.manageMembersRoles().contains(role);
  }

  public static boolean canDeleteAccount(AccountMemberRole role) {
    return role != null && AccountMemberRole.destructiveRoles().contains(role);
  }
}
