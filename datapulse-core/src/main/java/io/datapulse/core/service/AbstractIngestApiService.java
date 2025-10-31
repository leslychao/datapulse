package io.datapulse.core.service;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.domain.dto.LongBaseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

public abstract class AbstractIngestApiService<CreateReq, UpdateReq, Resp,
    D extends LongBaseDto,
    E extends LongBaseEntity>
    extends AbstractCrudService<D, E> {

  public AbstractIngestApiService(
      MapperFacade mapper,
      JpaRepository<E, Long> repository) {
    super(mapper, repository);
  }

  protected abstract Class<D> dtoType();

  protected abstract Class<E> entityType();

  protected abstract Class<Resp> responseType();

  @Override
  protected abstract E merge(@NotNull E target, @Valid @NotNull D source);

  public final Resp saveFromRequest(@Valid @NotNull CreateReq request) {
    D dto = mapper().to(request, dtoType());
    D saved = save(dto);
    return mapper().to(saved, responseType());
  }

  public final Resp saveFromUpdateRequest(@Valid @NotNull UpdateReq request) {
    D dto = toDtoFromUpdate(request);
    D saved = save(dto);
    return mapper().to(saved, responseType());
  }

  protected D toDtoFromUpdate(@NotNull UpdateReq request) {
    return mapper().to(request, dtoType());
  }
}
