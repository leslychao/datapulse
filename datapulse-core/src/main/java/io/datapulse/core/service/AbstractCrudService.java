package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.*;

import io.datapulse.core.converter.BeanConverter;
import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.dto.LongBaseDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public abstract class AbstractCrudService<T extends LongBaseDto, E extends LongBaseEntity> {

  public T save(@Valid @NotNull(message = DTO_REQUIRED) T dto) {
    E entity = mapToEntity(dto);
    E prepared = entityPreSaveAction(entity);
    E persisted = getRepository().save(prepared);
    return mapToDto(persisted);
  }

  public List<T> saveAll(@NotEmpty(message = LIST_REQUIRED)
  List<@Valid @NotNull(message = DTO_REQUIRED) T> dtos) {
    List<E> entities = dtos.stream()
        .map(this::mapToEntity)
        .map(this::entityPreSaveAction)
        .toList();
    return getRepository().saveAll(entities).stream().map(this::mapToDto).toList();
  }

  public T update(@Valid @NotNull(message = DTO_REQUIRED) T dto) {
    return updateInternal(dto, getRepository()::save);
  }

  public T updateAndFlush(@Valid @NotNull(message = DTO_REQUIRED) T dto) {
    return updateInternal(dto, getRepository()::saveAndFlush);
  }

  public List<T> updateAll(@NotEmpty(message = LIST_REQUIRED)
  List<@Valid @NotNull(message = DTO_REQUIRED) T> dtos) {
    return dtos.stream().map(this::updateAndFlush).toList();
  }

  public Optional<T> get(@NotNull(message = ID_REQUIRED) Long id) {
    return getRepository().findById(id).map(this::mapToDto);
  }

  public Page<T> getAllPageable(@NotNull(message = PAGEABLE_REQUIRED) Pageable pageable) {
    return getRepository().findAll(pageable).map(this::mapToDto);
  }

  public List<T> getAll() {
    return getRepository().findAll().stream().map(this::mapToDto).toList();
  }

  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    try {
      getRepository().deleteById(id);
    } catch (EmptyResultDataAccessException ex) {
      throw new NotFoundException(NOT_FOUND, id);
    }
  }

  private T updateInternal(@Valid @NotNull(message = DTO_REQUIRED) T dto,
      @NotNull(message = DTO_REQUIRED) Function<E, E> persistFunction) {
    if (dto.getId() == null) {
      throw new AppException(ID_REQUIRED);
    }
    E entity = getRepository().findById(dto.getId())
        .map(found -> updateEntityWithDto(found, dto))
        .map(this::entityPreUpdateAction)
        .map(persistFunction)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, dto.getId()));
    return mapToDto(entity);
  }

  protected E entityPreSaveAction(@NotNull(message = DTO_REQUIRED) E entity) {
    return entity;
  }

  protected E entityPreUpdateAction(@NotNull(message = DTO_REQUIRED) E entity) {
    return entity;
  }

  protected E mapToEntity(@Valid @NotNull(message = DTO_REQUIRED) T dto) {
    return getConverter().mapToEntity(dto);
  }

  protected T mapToDto(@NotNull(message = DTO_REQUIRED) E entity) {
    return getConverter().mapToDto(entity);
  }

  protected abstract BeanConverter<T, E> getConverter();

  protected abstract JpaRepository<E, Long> getRepository();

  protected abstract E updateEntityWithDto(@NotNull(message = DTO_REQUIRED) E entity,
      @Valid @NotNull(message = DTO_REQUIRED) T dto);
}
