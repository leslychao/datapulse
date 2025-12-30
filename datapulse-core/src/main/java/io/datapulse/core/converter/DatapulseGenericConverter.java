package io.datapulse.core.converter;

import static io.datapulse.domain.MessageCodes.CONVERSION_MAPPING_NOT_FOUND;

import io.datapulse.core.entity.AccountConnectionEntity;
import io.datapulse.core.entity.AccountEntity;
import io.datapulse.core.entity.EtlExecutionAuditEntity;
import io.datapulse.core.entity.inventory.FactInventorySnapshotEntity;
import io.datapulse.core.entity.productcost.ProductCostEntity;
import io.datapulse.core.mapper.AccountConnectionMapper;
import io.datapulse.core.mapper.AccountMapper;
import io.datapulse.core.mapper.EtlExecutionAuditMapper;
import io.datapulse.core.mapper.inventory.InventorySnapshotMapper;
import io.datapulse.core.mapper.productcost.ProductCostMapper;
import io.datapulse.domain.dto.AccountConnectionDto;
import io.datapulse.domain.dto.AccountDto;
import io.datapulse.domain.dto.EtlExecutionAuditDto;
import io.datapulse.domain.dto.inventory.InventorySnapshotDto;
import io.datapulse.domain.dto.productcost.ProductCostDto;
import io.datapulse.domain.dto.request.AccountConnectionCreateRequest;
import io.datapulse.domain.dto.request.AccountConnectionUpdateRequest;
import io.datapulse.domain.dto.request.AccountCreateRequest;
import io.datapulse.domain.dto.request.AccountUpdateRequest;
import io.datapulse.domain.dto.request.inventory.InventorySnapshotQueryRequest;
import io.datapulse.domain.dto.response.AccountConnectionResponse;
import io.datapulse.domain.dto.response.AccountResponse;
import io.datapulse.domain.dto.response.inventory.InventorySnapshotResponse;
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

  private final Map<ConvertiblePair, Function<Object, Object>> converters = new LinkedHashMap<>();

  @PostConstruct
  void init() {
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

    converters.put(
        new ConvertiblePair(InventorySnapshotQueryRequest.class, InventorySnapshotDto.class),
        src -> inventorySnapshotMapper.toDto((InventorySnapshotQueryRequest) src)
    );

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
        new ConvertiblePair(EtlExecutionAuditDto.class, EtlExecutionAuditEntity.class),
        src -> etlSyncAuditMapper.toEntity((EtlExecutionAuditDto) src)
    );
    converters.put(
        new ConvertiblePair(EtlExecutionAuditEntity.class, EtlExecutionAuditDto.class),
        src -> etlSyncAuditMapper.toDto((EtlExecutionAuditEntity) src)
    );

    converters.put(
        new ConvertiblePair(FactInventorySnapshotEntity.class, InventorySnapshotDto.class),
        src -> inventorySnapshotMapper.toDto((FactInventorySnapshotEntity) src)
    );

    converters.put(
        new ConvertiblePair(AccountDto.class, AccountResponse.class),
        src -> accountMapper.toResponse((AccountDto) src)
    );

    converters.put(
        new ConvertiblePair(AccountConnectionDto.class, AccountConnectionResponse.class),
        src -> accountConnectionMapper.toResponse((AccountConnectionDto) src)
    );

    converters.put(
        new ConvertiblePair(InventorySnapshotDto.class, InventorySnapshotResponse.class),
        src -> inventorySnapshotMapper.toResponse((InventorySnapshotDto) src)
    );

    converters.put(
        new ConvertiblePair(ProductCostDto.class, ProductCostEntity.class),
        src -> productCostMapper.toEntity((ProductCostDto) src)
    );
    converters.put(
        new ConvertiblePair(ProductCostEntity.class, ProductCostDto.class),
        src -> productCostMapper.toDto((ProductCostEntity) src)
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
