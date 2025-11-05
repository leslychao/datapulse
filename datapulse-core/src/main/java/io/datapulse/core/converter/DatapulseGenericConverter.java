package io.datapulse.core.converter;

import static io.datapulse.domain.MessageCodes.CONVERSION_MAPPING_NOT_FOUND;

import io.datapulse.core.mapper.AccountConnectionMapper;
import io.datapulse.core.mapper.AccountMapper;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.exception.AppException;
import jakarta.annotation.PostConstruct;
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
  private final Map<ConvertiblePair, Function<Object, Object>> registry = new java.util.LinkedHashMap<>();

  @PostConstruct
  void init() {
    // REQUEST → DTO
    registry.put(new ConvertiblePair(AccountCreateRequest.class, AccountDto.class),
        src -> accountMapper.fromCreateRequest((AccountCreateRequest) src));

    registry.put(
        new ConvertiblePair(AccountConnectionCreateRequest.class, AccountConnectionDto.class),
        src -> accountConnectionMapper.mapCreateRequestToDto((AccountConnectionCreateRequest) src));

    // DTO → Entity
    // Entity → DTO
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
    var fn = registry.get(new ConvertiblePair(sourceType.getType(), targetType.getType()));
    if (fn == null) {
      throw new AppException(CONVERSION_MAPPING_NOT_FOUND,
          sourceType.getType().getSimpleName(),
          targetType.getType().getSimpleName());
    }
    return fn.apply(source);
  }
}

