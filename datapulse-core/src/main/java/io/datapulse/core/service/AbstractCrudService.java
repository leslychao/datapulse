package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.DTO_REQUIRED;
import static io.datapulse.domain.MessageCodes.ID_REQUIRED;
import static io.datapulse.domain.MessageCodes.LIST_REQUIRED;
import static io.datapulse.domain.MessageCodes.NOT_FOUND;
import static io.datapulse.domain.MessageCodes.PAGEABLE_REQUIRED;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.domain.dto.LongBaseDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@RequiredArgsConstructor
public abstract class AbstractCrudService<D extends LongBaseDto, E extends LongBaseEntity> {

  private final MapperFacade mapper;

  protected MapperFacade mapper() {
    return mapper;
  }

  protected abstract Class<D> dtoType();

  protected abstract Class<E> entityType();

  protected abstract JpaRepository<E, Long> repository();

  public D save(@Valid @NotNull(message = DTO_REQUIRED) D dto) {
    E entity = mapper.to(dto, entityType());
    E persisted = repository().save(beforeSave(entity));
    return mapper.to(persisted, dtoType());
  }

  public List<D> saveAll(
      @NotEmpty(message = LIST_REQUIRED)
      List<@Valid @NotNull(message = DTO_REQUIRED) D> dtos) {
    return repository()
        .saveAll(
            dtos.stream()
                .map(d -> mapper.to(d, entityType()))
                .map(this::beforeSave)
                .toList())
        .stream()
        .map(e -> mapper.to(e, dtoType()))
        .toList();
  }

  public D update(@Valid @NotNull(message = DTO_REQUIRED) D dto) {
    return doUpdate(dto, repository()::save);
  }

  public D updateAndFlush(@Valid @NotNull(message = DTO_REQUIRED) D dto) {
    return doUpdate(dto, repository()::saveAndFlush);
  }

  public Optional<D> get(@NotNull(message = ID_REQUIRED) Long id) {
    return repository().findById(id).map(e -> mapper.to(e, dtoType()));
  }

  public Page<D> getAllPageable(@NotNull(message = PAGEABLE_REQUIRED) Pageable pageable) {
    return repository().findAll(pageable).map(e -> mapper.to(e, dtoType()));
  }

  public List<D> getAll() {
    return repository().findAll().stream().map(e -> mapper.to(e, dtoType())).toList();
  }

  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    try {
      repository().deleteById(id);
    } catch (EmptyResultDataAccessException ex) {
      throw new NotFoundException(NOT_FOUND, id);
    }
  }

  private D doUpdate(@Valid @NotNull(message = DTO_REQUIRED) D dto,
      @NotNull Function<E, E> persistFn) {
    if (dto.getId() == null) {
      throw new AppException(ID_REQUIRED);
    }
    E entity = repository()
        .findById(dto.getId())
        .map(found -> merge(found, dto))
        .map(this::beforeUpdate)
        .map(persistFn)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, dto.getId()));
    return mapper.to(entity, dtoType());
  }

  protected E beforeSave(E entity) {
    return entity;
  }

  protected E beforeUpdate(E entity) {
    return entity;
  }

  protected abstract E merge(@NotNull E target, @Valid @NotNull D source);
}
