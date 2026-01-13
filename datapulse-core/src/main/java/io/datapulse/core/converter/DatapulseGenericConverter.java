package io.datapulse.core.converter;

import static io.datapulse.domain.MessageCodes.CONVERSION_MAPPING_NOT_FOUND;

import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.core.entity.account.AccountConnectionEntity;
import io.datapulse.core.entity.account.AccountEntity;
import io.datapulse.core.entity.account.AccountMemberEntity;
import io.datapulse.core.entity.inventory.FactInventorySnapshotEntity;
import io.datapulse.core.entity.productcost.ProductCostEntity;
import io.datapulse.core.entity.userprofile.UserProfileEntity;
import io.datapulse.core.mapper.AccountMemberMapper;
import io.datapulse.core.mapper.EtlExecutionAuditMapper;
import io.datapulse.core.mapper.account.AccountConnectionMapper;
import io.datapulse.core.mapper.account.AccountMapper;
import io.datapulse.core.mapper.inventory.InventorySnapshotMapper;
import io.datapulse.core.mapper.productcost.ProductCostMapper;
import io.datapulse.core.mapper.userprofile.UserProfileMapper;
import io.datapulse.domain.dto.AccountMemberDto;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import io.datapulse.domain.dto.account.AccountConnectionDto;
import io.datapulse.domain.dto.account.AccountDto;
import io.datapulse.domain.dto.productcost.ProductCostDto;
import io.datapulse.domain.request.AccountMemberCreateRequest;
import io.datapulse.domain.request.AccountMemberUpdateRequest;
import io.datapulse.domain.request.account.AccountConnectionCreateRequest;
import io.datapulse.domain.request.account.AccountConnectionUpdateRequest;
import io.datapulse.domain.request.account.AccountCreateRequest;
import io.datapulse.domain.request.account.AccountUpdateRequest;
import io.datapulse.domain.request.userprofile.UserProfileCreateRequest;
import io.datapulse.domain.request.userprofile.UserProfileUpdateRequest;
import io.datapulse.domain.response.AccountMemberResponse;
import io.datapulse.domain.response.account.AccountConnectionResponse;
import io.datapulse.domain.response.account.AccountResponse;
import io.datapulse.domain.response.inventory.InventorySnapshotResponse;
import io.datapulse.domain.response.userprofile.UserProfileResponse;
import io.datapulse.domain.dto.userprofile.UserProfileDto;
import io.datapulse.domain.exception.AppException;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class DatapulseGenericConverter implements GenericConverter {

  private final AccountMapper accountMapper;
  private final AccountConnectionMapper accountConnectionMapper;
  private final EtlExecutionAuditMapper etlSyncAuditMapper;
  private final InventorySnapshotMapper inventorySnapshotMapper;
  private final ProductCostMapper productCostMapper;

  private final UserProfileMapper userProfileMapper;
  private final AccountMemberMapper accountMemberMapper;

  private final Map<ConvertiblePair, TypedConverter<?, ?>> converters = new LinkedHashMap<>();

  @PostConstruct
  void init() {
    // ===== Requests -> DTO =====
    register(AccountCreateRequest.class, AccountDto.class, accountMapper::toDto);
    register(AccountUpdateRequest.class, AccountDto.class, accountMapper::toDto);

    register(AccountConnectionCreateRequest.class, AccountConnectionDto.class,
        accountConnectionMapper::toDto);
    register(AccountConnectionUpdateRequest.class, AccountConnectionDto.class,
        accountConnectionMapper::toDto);

    register(UserProfileCreateRequest.class, UserProfileDto.class, userProfileMapper::toDto);
    register(UserProfileUpdateRequest.class, UserProfileDto.class, userProfileMapper::toDto);

    register(AccountMemberCreateRequest.class, AccountMemberDto.class, accountMemberMapper::toDto);
    register(AccountMemberUpdateRequest.class, AccountMemberDto.class, accountMemberMapper::toDto);

    // ===== DTO <-> Entity =====
    register(AccountDto.class, AccountEntity.class, accountMapper::toEntity);
    register(AccountEntity.class, AccountDto.class, accountMapper::toDto);

    register(AccountConnectionDto.class, AccountConnectionEntity.class,
        accountConnectionMapper::toEntity);
    register(AccountConnectionEntity.class, AccountConnectionDto.class,
        accountConnectionMapper::toDto);

    register(EtlExecutionAuditDto.class, EtlExecutionAuditEntity.class,
        etlSyncAuditMapper::toEntity);
    register(EtlExecutionAuditEntity.class, EtlExecutionAuditDto.class, etlSyncAuditMapper::toDto);

    register(ProductCostDto.class, ProductCostEntity.class, productCostMapper::toEntity);
    register(ProductCostEntity.class, ProductCostDto.class, productCostMapper::toDto);

    register(UserProfileDto.class, UserProfileEntity.class, userProfileMapper::toEntity);
    register(UserProfileEntity.class, UserProfileDto.class, userProfileMapper::toDto);

    register(AccountMemberDto.class, AccountMemberEntity.class, accountMemberMapper::toEntity);
    register(AccountMemberEntity.class, AccountMemberDto.class, accountMemberMapper::toDto);

    // ===== Entity/DTO -> Response =====
    register(AccountDto.class, AccountResponse.class, accountMapper::toResponse);
    register(AccountConnectionDto.class, AccountConnectionResponse.class,
        accountConnectionMapper::toResponse);
    register(FactInventorySnapshotEntity.class, InventorySnapshotResponse.class,
        inventorySnapshotMapper::toResponse);

    register(UserProfileDto.class, UserProfileResponse.class, userProfileMapper::toResponse);
    register(AccountMemberDto.class, AccountMemberResponse.class, accountMemberMapper::toResponse);
  }

  @Override
  public Set<ConvertiblePair> getConvertibleTypes() {
    return converters.keySet();
  }

  @Override
  public Object convert(
      Object source,
      @NonNull TypeDescriptor sourceType,
      @NonNull TypeDescriptor targetType
  ) {
    if (source == null) {
      return null;
    }

    Class<?> srcClass = sourceType.getType();
    Class<?> tgtClass = targetType.getType();

    TypedConverter<?, ?> direct = converters.get(new ConvertiblePair(srcClass, tgtClass));
    if (direct != null) {
      return direct.convert(source);
    }

    TypedConverter<?, ?> compatible = findCompatibleConverter(srcClass, tgtClass);
    if (compatible != null) {
      return compatible.convert(source);
    }

    throw new AppException(
        CONVERSION_MAPPING_NOT_FOUND,
        srcClass.getSimpleName(),
        tgtClass.getSimpleName()
    );
  }

  private TypedConverter<?, ?> findCompatibleConverter(Class<?> sourceClass, Class<?> targetClass) {
    for (Map.Entry<ConvertiblePair, TypedConverter<?, ?>> entry : converters.entrySet()) {
      ConvertiblePair pair = entry.getKey();
      if (pair.getSourceType().isAssignableFrom(sourceClass)
          && pair.getTargetType().isAssignableFrom(targetClass)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private <S, T> void register(
      Class<S> sourceType,
      Class<T> targetType,
      Function<S, T> converter
  ) {
    converters.put(new ConvertiblePair(sourceType, targetType),
        new TypedConverter<>(sourceType, converter));
  }

  private record TypedConverter<S, T>(Class<S> sourceType, Function<S, T> converter) {

    private Object convert(Object source) {
      return converter.apply(sourceType.cast(source));
    }
  }
}
