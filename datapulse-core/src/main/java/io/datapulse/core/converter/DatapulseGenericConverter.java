package io.datapulse.core.converter;

import static io.datapulse.domain.MessageCodes.CONVERSION_MAPPING_NOT_FOUND;

import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.entity.EtlSyncAuditEntity;
import io.datapulse.core.mapper.AccountConnectionMapper;
import io.datapulse.core.mapper.AccountMapper;
import io.datapulse.core.mapper.EtlSyncAuditMapper;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.EtlSyncAuditDto;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.dto.response.AccountResponse;
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
  private final EtlSyncAuditMapper etlSyncAuditMapper;

  private final Map<ConvertiblePair, Function<Object, Object>> converters = new LinkedHashMap<>();

  @PostConstruct
  void init() {
    // ===== REQUEST → DTO
    converters.put(
        new ConvertiblePair(AccountCreateRequest.class, AccountDto.class),
        src -> accountMapper.toDto((AccountCreateRequest) src)
    );

    converters.put(
        new ConvertiblePair(AccountUpdateRequest.class, AccountDto.class),
        src -> accountMapper.toDto((AccountUpdateRequest) src)
    );

    converters.put(
        new ConvertiblePair(AccountConnectionCreateRequest.class, AccountConnectionDto.class),
        src -> accountConnectionMapper.toDto((AccountConnectionCreateRequest) src)
    );

    converters.put(
        new ConvertiblePair(AccountConnectionUpdateRequest.class, AccountConnectionDto.class),
        src -> accountConnectionMapper.toDto((AccountConnectionUpdateRequest) src)
    );

    // ===== DTO ↔ ENTITY
    converters.put(
        new ConvertiblePair(AccountDto.class, AccountEntity.class),
        src -> accountMapper.toEntity((AccountDto) src)
    );
    converters.put(
        new ConvertiblePair(AccountEntity.class, AccountDto.class),
        src -> accountMapper.toDto((AccountEntity) src)
    );

    converters.put(
        new ConvertiblePair(AccountConnectionDto.class, AccountConnectionEntity.class),
        src -> accountConnectionMapper.toEntity((AccountConnectionDto) src)
    );
    converters.put(
        new ConvertiblePair(AccountConnectionEntity.class, AccountConnectionDto.class),
        src -> accountConnectionMapper.toDto((AccountConnectionEntity) src)
    );

    converters.put(
        new ConvertiblePair(EtlSyncAuditDto.class, EtlSyncAuditEntity.class),
        src -> etlSyncAuditMapper.toEntity((EtlSyncAuditDto) src)
    );
    converters.put(
        new ConvertiblePair(EtlSyncAuditEntity.class, EtlSyncAuditDto.class),
        src -> etlSyncAuditMapper.toDto((EtlSyncAuditEntity) src)
    );

    // ===== DTO → RESPONSE
    converters.put(
        new ConvertiblePair(AccountDto.class, AccountResponse.class),
        src -> accountMapper.toResponse((AccountDto) src)
    );

    converters.put(
        new ConvertiblePair(AccountConnectionDto.class, AccountConnectionResponse.class),
        src -> accountConnectionMapper.toResponse((AccountConnectionDto) src)
    );
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

    Function<Object, Object> converter =
        converters.get(new ConvertiblePair(srcClass, tgtClass));
    if (converter != null) {
      return converter.apply(source);
    }

    Function<Object, Object> compatibleConverter =
        findCompatibleConverter(srcClass, tgtClass);
    if (compatibleConverter != null) {
      return compatibleConverter.apply(source);
    }

    throw new AppException(
        CONVERSION_MAPPING_NOT_FOUND,
        srcClass.getSimpleName(),
        tgtClass.getSimpleName()
    );
  }

  private Function<Object, Object> findCompatibleConverter(
      Class<?> sourceClass,
      Class<?> targetClass
  ) {
    for (Map.Entry<ConvertiblePair, Function<Object, Object>> entry : converters.entrySet()) {
      ConvertiblePair pair = entry.getKey();
      if (pair.getSourceType().isAssignableFrom(sourceClass)
          && pair.getTargetType().isAssignableFrom(targetClass)) {
        return entry.getValue();
      }
    }
    return null;
  }
}
