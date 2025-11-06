package io.datapulse.core.converter;

import static io.datapulse.domain.MessageCodes.CONVERSION_MAPPING_NOT_FOUND;

import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.mapper.AccountConnectionMapper;
import io.datapulse.core.mapper.AccountMapper;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.AccountDto;
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
import java.util.Optional;
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

  /**
   * Важно: сохраняем порядок регистрации (LinkedHashMap)
   */
  private final Map<ConvertiblePair, Function<Object, Object>> registry = new LinkedHashMap<>();

  @PostConstruct
  void init() {
    // ===== REQUEST → DTO
    registry.put(new ConvertiblePair(AccountCreateRequest.class, AccountDto.class),
        src -> accountMapper.toDto((AccountCreateRequest) src));

    registry.put(new ConvertiblePair(AccountUpdateRequest.class, AccountDto.class),
        src -> accountMapper.toDto((AccountUpdateRequest) src));

    registry.put(
        new ConvertiblePair(AccountConnectionCreateRequest.class, AccountConnectionDto.class),
        src -> accountConnectionMapper.toDto((AccountConnectionCreateRequest) src));

    registry.put(
        new ConvertiblePair(AccountConnectionUpdateRequest.class, AccountConnectionDto.class),
        src -> accountConnectionMapper.toDto((AccountConnectionUpdateRequest) src));

    // ===== DTO ↔ ENTITY
    registry.put(new ConvertiblePair(AccountDto.class, AccountEntity.class),
        src -> accountMapper.toEntity((AccountDto) src));
    registry.put(new ConvertiblePair(AccountEntity.class, AccountDto.class),
        src -> accountMapper.toDto((AccountEntity) src));

    registry.put(new ConvertiblePair(AccountConnectionDto.class, AccountConnectionEntity.class),
        src -> accountConnectionMapper.toEntity((AccountConnectionDto) src));
    registry.put(new ConvertiblePair(AccountConnectionEntity.class, AccountConnectionDto.class),
        src -> accountConnectionMapper.toDto((AccountConnectionEntity) src));

    // ===== DTO → RESPONSE
    registry.put(new ConvertiblePair(AccountDto.class, AccountResponse.class),
        src -> accountMapper.toResponse((AccountDto) src));

    registry.put(new ConvertiblePair(AccountConnectionDto.class, AccountConnectionResponse.class),
        src -> accountConnectionMapper.toResponse((AccountConnectionDto) src));
  }

  @Override
  public Set<ConvertiblePair> getConvertibleTypes() {
    return registry.keySet();
  }

  @Override
  public Object convert(Object source,
      @NonNull TypeDescriptor sourceType,
      @NonNull TypeDescriptor targetType) {
    if (source == null) {
      return null;
    }

    Function<Object, Object> fn = registry.get(
        new ConvertiblePair(sourceType.getType(), targetType.getType()));
    if (fn != null) {
      return fn.apply(source);
    }

    Optional<Map.Entry<ConvertiblePair, Function<Object, Object>>> compatible = registry.entrySet()
        .stream()
        .filter(e -> e.getKey().getSourceType().isAssignableFrom(sourceType.getType())
            && e.getKey().getTargetType().isAssignableFrom(targetType.getType()))
        .findFirst();

    if (compatible.isPresent()) {
      return compatible.get().getValue().apply(source);
    }

    throw new AppException(CONVERSION_MAPPING_NOT_FOUND,
        sourceType.getType().getSimpleName(),
        targetType.getType().getSimpleName());
  }
}
