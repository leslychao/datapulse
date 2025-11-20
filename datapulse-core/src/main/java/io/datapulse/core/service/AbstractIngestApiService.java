package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ID_REQUIRED;
import static io.datapulse.domain.MessageCodes.REQUEST_REQUIRED;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.dto.LongBaseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;

public abstract class AbstractIngestApiService<
    CR, UR, R,
    D extends LongBaseDto,
    E extends LongBaseEntity
    > extends AbstractCrudService<D, E> {

  protected abstract Class<R> responseType();

  protected final R toResponse(D dto) {
    return mapper().to(dto, responseType());
  }

  @Transactional
  public R createFromRequest(@Valid @NotNull(message = REQUEST_REQUIRED) CR request) {
    D draft = mapper().to(request, dtoType());
    D saved = save(draft);
    return toResponse(saved);
  }

  @Transactional
  public R updateFromRequest(
      @NotNull(message = ID_REQUIRED) Long id,
      @Valid @NotNull(message = REQUEST_REQUIRED) UR request) {
    D patch = mapper().to(request, dtoType());
    patch.setId(id);
    D updated = update(patch);
    return toResponse(updated);
  }
}
