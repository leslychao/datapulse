package io.datapulse.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AccountMemberRole {
  OWNER,
  ADMIN,
  OPERATOR,
  VIEWER;

  public static Set<AccountMemberRole> writeRoles() {
    return EnumSet.of(OWNER, ADMIN, OPERATOR);
  }

  public static Set<AccountMemberRole> manageMembersRoles() {
    return EnumSet.of(OWNER, ADMIN);
  }

  public static Set<AccountMemberRole> destructiveRoles() {
    return EnumSet.of(OWNER);
  }
}
