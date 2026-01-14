package io.datapulse.core.service;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
import io.datapulse.domain.dto.LongBaseDto;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  public R createFromRequest(
      @Valid
      @NotNull(message = ValidationKeys.REQUEST_REQUIRED)
      CR request
  ) {
    D draft = mapper().to(request, dtoType());
    D saved = save(draft);
    return toResponse(saved);
  }

  @Transactional
  public R updateFromRequest(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id,
      @Valid
      @NotNull(message = ValidationKeys.REQUEST_REQUIRED)
      UR request
  ) {
    D patch = mapper().to(request, dtoType());
    patch.setId(id);
    D updated = update(patch);
    return toResponse(updated);
  }

  @Transactional(readOnly = true)
  public Optional<R> getResponse(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id
  ) {
    return super.get(id).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public R getResponseRequired(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id
  ) {
    return getResponse(id)
        .orElseThrow(() -> new NotFoundException(MessageCodes.NOT_FOUND, id));
  }

  @Transactional(readOnly = true)
  public Page<R> getAllResponsePageable(
      @NotNull(message = ValidationKeys.PAGEABLE_REQUIRED)
      Pageable pageable
  ) {
    return super.getAllPageable(pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public List<R> getAllResponses() {
    return super.getAll()
        .stream()
        .map(this::toResponse)
        .toList();
  }
}
