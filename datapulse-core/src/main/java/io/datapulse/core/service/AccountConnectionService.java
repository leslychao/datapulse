package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.DTO_REQUIRED;
import static io.datapulse.domain.MessageCodes.ENTITY_REQUIRED;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.domain.dto.LongBaseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

public abstract class AbstractApiIngestService<CR, UR, R,
    D extends LongBaseDto,
    E extends LongBaseEntity>
    extends AbstractCrudService<D, E> {

  protected AbstractApiIngestService(MapperFacade mapper,
      JpaRepository<E, Long> repository) {
    super(mapper, repository);
  }

  protected abstract Class<D> dtoClass();

  protected abstract Class<E> entityClass();

  protected abstract Class<R> responseClass();

  @Override
  protected abstract E merge(@NotNull(message = ENTITY_REQUIRED) E target,
      @Valid @NotNull(message = DTO_REQUIRED) D source);

  public final R createFromRequest(@Valid @NotNull(message = DTO_REQUIRED) CR request) {
    D dto = mapper().to(request, dtoClass());
    D saved = save(dto);
    return mapper().to(saved, responseClass());
  }

  public final R updateFromRequest(@Valid @NotNull(message = DTO_REQUIRED) UR request) {
    D dto = mapUpdateRequestToDto(request);
    D saved = save(dto);
    return mapper().to(saved, responseClass());
  }

  protected D mapUpdateRequestToDto(@NotNull(message = DTO_REQUIRED) UR request) {
    return mapper().to(request, dtoClass());
  }
}
