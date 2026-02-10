package io.datapulse.core.service.account.invite;

import io.datapulse.core.entity.account.invite.AccountInviteAcceptanceEntity;
import io.datapulse.core.entity.account.invite.AccountInviteEntity;
import io.datapulse.core.entity.account.invite.AccountInviteTargetEntity;
import io.datapulse.core.mapper.account.invite.AccountInviteMapper;
import io.datapulse.core.repository.AccountMemberRepository;
import io.datapulse.core.repository.account.invite.AccountInviteAcceptanceRepository;
import io.datapulse.core.repository.account.invite.AccountInviteRepository;
import io.datapulse.core.repository.account.invite.AccountInviteTargetRepository;
import io.datapulse.core.service.AccountMemberService;
import io.datapulse.domain.AccountInviteStatus;
import io.datapulse.domain.AccountMemberRole;
import io.datapulse.domain.AccountMemberStatus;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.BadRequestException;
import io.datapulse.domain.exception.InternalServerErrorException;
import io.datapulse.domain.exception.NotFoundException;
import io.datapulse.domain.request.account.invite.AccountInviteAcceptRequest;
import io.datapulse.domain.request.account.invite.AccountInviteCreateRequest;
import io.datapulse.domain.response.account.invite.AccountInviteAcceptResponse;
import io.datapulse.domain.response.account.invite.AccountInviteResolveResponse;
import io.datapulse.domain.response.account.invite.AccountInviteResolveResponse.ResolveState;
import io.datapulse.domain.response.account.invite.AccountInviteResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountInviteService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final AccountInviteRepository accountInviteRepository;
  private final AccountInviteTargetRepository accountInviteTargetRepository;
  private final AccountInviteAcceptanceRepository accountInviteAcceptanceRepository;

  private final AccountMemberService accountMemberService;
  private final AccountMemberRepository accountMemberRepository;

  private final AccountInviteMapper accountInviteMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public AccountInviteResponse createInvite(
      long createdByProfileId,
      AccountInviteCreateRequest request
  ) {
    String normalizedEmail = normalizeEmail(request.email());
    if (normalizedEmail == null) {
      throw new BadRequestException(MessageCodes.INVITE_EMAIL_REQUIRED);
    }

    if (request.initialRole() == AccountMemberRole.OWNER) {
      throw new BadRequestException(MessageCodes.INVITE_OWNER_ROLE_FORBIDDEN);
    }

    List<Long> distinctAccountIds = request.accountIds().stream().distinct().toList();
    if (distinctAccountIds.isEmpty()) {
      throw new BadRequestException(MessageCodes.INVITE_ACCOUNTS_REQUIRED);
    }

    String rawToken = generateRawToken();
    String tokenHash = hashToken(rawToken);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime expiresAt = now.plusDays(3);

    AccountInviteEntity invite = new AccountInviteEntity();
    invite.setEmail(normalizedEmail);
    invite.setTokenHash(tokenHash);
    invite.setStatus(AccountInviteStatus.PENDING);
    invite.setExpiresAt(expiresAt);
    invite.setCreatedByProfileId(createdByProfileId);

    AccountInviteEntity savedInvite = accountInviteRepository.save(invite);

    List<AccountInviteTargetEntity> targets = distinctAccountIds.stream()
        .map(accountId -> {
          AccountInviteTargetEntity target = new AccountInviteTargetEntity();
          target.setInviteId(savedInvite.getId());
          target.setAccountId(accountId);
          target.setInitialRole(request.initialRole());
          return target;
        })
        .toList();

    accountInviteTargetRepository.saveAll(targets);
    eventPublisher.publishEvent(new AccountInviteCreatedEvent(normalizedEmail, rawToken));

    return accountInviteMapper.toResponse(savedInvite, targets);
  }

  @Transactional(readOnly = true)
  public AccountInviteResolveResponse resolve(String token) {
    return resolveInternal(hashToken(token), ResolveContext.anonymous());
  }

  @Transactional(readOnly = true)
  public AccountInviteResolveResponse resolveForAuthenticatedUser(
      long currentProfileId,
      String currentEmail,
      String token
  ) {
    return resolveInternal(
        hashToken(token),
        ResolveContext.authenticated(currentProfileId, normalizeEmail(currentEmail))
    );
  }

  @Transactional
  public AccountInviteAcceptResponse accept(
      long currentProfileId,
      String currentEmail,
      AccountInviteAcceptRequest request
  ) {
    String normalizedCurrentEmail = normalizeEmail(currentEmail);
    if (normalizedCurrentEmail == null) {
      throw new BadRequestException(MessageCodes.INVITE_CURRENT_EMAIL_NOT_DEFINED);
    }

    String tokenHash = hashToken(request.token());

    AccountInviteEntity invite = accountInviteRepository.findByTokenHashForUpdate(tokenHash)
        .orElseThrow(() -> new NotFoundException(MessageCodes.INVITE_NOT_FOUND_OR_INVALID));

    AccountInviteStatus currentStatus = invite.getStatus();

    if (currentStatus == AccountInviteStatus.CANCELLED) {
      throw new BadRequestException(MessageCodes.INVITE_CANCELLED);
    }

    if (!Objects.equals(normalizedCurrentEmail, invite.getEmail())) {
      throw new BadRequestException(MessageCodes.INVITE_AUTHENTICATED_EMAIL_MISMATCH);
    }

    List<AccountInviteTargetEntity> targets = accountInviteTargetRepository.findAllByInviteId(
        invite.getId());

    if (currentStatus == AccountInviteStatus.ACCEPTED) {
      List<Long> alreadyGranted = targets.stream()
          .map(AccountInviteTargetEntity::getAccountId)
          .toList();
      return new AccountInviteAcceptResponse(true, alreadyGranted);
    }

    if (isExpired(invite)) {
      invite.setStatus(AccountInviteStatus.EXPIRED);
      accountInviteRepository.save(invite);
      throw new BadRequestException(MessageCodes.INVITE_EXPIRED);
    }

    List<Long> grantedAccountIds = grantMemberships(currentProfileId, targets);

    AccountInviteAcceptanceEntity acceptance = new AccountInviteAcceptanceEntity();
    acceptance.setInviteId(invite.getId());
    acceptance.setAcceptedProfileId(currentProfileId);
    acceptance.setAcceptedAt(OffsetDateTime.now(ZoneOffset.UTC));
    accountInviteAcceptanceRepository.save(acceptance);

    invite.setStatus(AccountInviteStatus.ACCEPTED);
    accountInviteRepository.save(invite);

    return new AccountInviteAcceptResponse(true, grantedAccountIds);
  }

  private boolean isMemberOfAnyActive(long profileId, List<AccountInviteTargetEntity> targets) {
    for (AccountInviteTargetEntity target : targets) {
      boolean exists = accountMemberRepository.existsByAccount_IdAndUser_IdAndStatus(
          target.getAccountId(),
          profileId,
          AccountMemberStatus.ACTIVE
      );
      if (exists) {
        return true;
      }
    }
    return false;
  }

  private List<Long> grantMemberships(long profileId, List<AccountInviteTargetEntity> targets) {
    for (AccountInviteTargetEntity target : targets) {
      accountMemberService.grantAccountMembership(
          target.getAccountId(),
          profileId,
          target.getInitialRole()
      );
    }

    return targets.stream()
        .map(AccountInviteTargetEntity::getAccountId)
        .toList();
  }

  private static boolean isExpired(AccountInviteEntity invite) {
    return OffsetDateTime.now(ZoneOffset.UTC).isAfter(invite.getExpiresAt());
  }

  private static String normalizeEmail(String email) {
    if (email == null) {
      return null;
    }
    String normalized = email.trim().toLowerCase();
    return normalized.isBlank() ? null : normalized;
  }

  private AccountInviteResolveResponse resolveInternal(String tokenHash, ResolveContext context) {
    AccountInviteEntity invite = accountInviteRepository.findByTokenHash(tokenHash).orElse(null);
    if (invite == null) {
      return accountInviteMapper.toResolveResponse(ResolveState.INVALID, null, List.of());
    }

    AccountInviteStatus currentStatus = invite.getStatus();

    if (currentStatus == AccountInviteStatus.CANCELLED) {
      return accountInviteMapper.toResolveResponse(ResolveState.CANCELLED, invite, List.of());
    }

    if (isExpired(invite)) {
      return accountInviteMapper.toResolveResponse(ResolveState.EXPIRED, invite, List.of());
    }

    List<AccountInviteTargetEntity> targets = accountInviteTargetRepository.findAllByInviteId(
        invite.getId()
    );

    if (currentStatus == AccountInviteStatus.ACCEPTED) {
      return accountInviteMapper.toResolveResponse(ResolveState.ALREADY_ACCEPTED, invite, targets);
    }

    if (context.isAnonymous()) {
      return accountInviteMapper.toResolveResponse(ResolveState.PENDING, invite, targets);
    }

    if (!Objects.equals(context.normalizedEmail(), invite.getEmail())) {
      return accountInviteMapper.toResolveResponse(
          ResolveState.AUTHENTICATED_EMAIL_MISMATCH,
          invite,
          targets
      );
    }

    boolean alreadyMember = isMemberOfAnyActive(context.profileId(), targets);
    if (alreadyMember) {
      return accountInviteMapper.toResolveResponse(
          ResolveState.AUTHENTICATED_ALREADY_MEMBER,
          invite,
          targets
      );
    }

    return accountInviteMapper.toResolveResponse(ResolveState.AUTHENTICATED_CAN_ACCEPT, invite,
        targets);
  }

  private record ResolveContext(boolean isAnonymous, long profileId, String normalizedEmail) {

    static ResolveContext anonymous() {
      return new ResolveContext(true, 0L, null);
    }

    static ResolveContext authenticated(long profileId, String normalizedEmail) {
      return new ResolveContext(false, profileId, normalizedEmail);
    }
  }

  private static String generateRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception ex) {
      throw InternalServerErrorException.inviteTokenHashingFailed(ex);
    }
  }
}
