package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.ID_REQUIRED;
import static io.datapulse.domain.MessageCodes.NOT_FOUND;

import io.datapulse.core.converter.BeanConverter;
import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.dto.LongBaseDto;
import io.datapulse.domain.exception.AppException;
import io.datapulse.domain.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public abstract class AbstractCrudService<T extends LongBaseDto, E extends LongBaseEntity> {

  public T save(@NonNull T dto) {
    E entity = mapToEntity(dto);
    E prepared = entityPreSaveAction(entity);
    E persisted = getRepository().save(prepared);
    return mapToDto(persisted);
  }

  public List<T> saveAll(@NonNull List<T> dtos) {
    List<E> entities = dtos.stream()
        .map(this::mapToEntity)
        .map(this::entityPreSaveAction)
        .toList();
    return getRepository().saveAll(entities).stream().map(this::mapToDto).toList();
  }

  public T update(@NonNull T dto) {
    return updateInternal(dto, getRepository()::save);
  }

  public T updateAndFlush(@NonNull T dto) {
    return updateInternal(dto, getRepository()::saveAndFlush);
  }

  public List<T> updateAll(@NonNull List<T> dtos) {
    return dtos.stream()
        .map(this::updateAndFlush)
        .toList();
  }

  public Optional<T> get(@NonNull Long id) {
    return getRepository().findById(id).map(this::mapToDto);
  }

  public Page<T> getAllPageable(@NonNull Pageable pageable) {
    return getRepository().findAll(pageable).map(this::mapToDto);
  }

  public List<T> getAll() {
    return getRepository().findAll().stream()
        .map(this::mapToDto)
        .toList();
  }

  public void delete(@NonNull Long id) {
    getRepository().deleteById(id);
  }

  private T updateInternal(@NonNull T dto, @NonNull Function<E, E> persistFunction) {
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

  protected E entityPreSaveAction(@NonNull E entity) {
    return entity;
  }

  protected E entityPreUpdateAction(@NonNull E entity) {
    return entity;
  }

  protected E mapToEntity(@NonNull T dto) {
    return getConverter().mapToEntity(dto);
  }

  protected T mapToDto(@NonNull E entity) {
    return getConverter().mapToDto(entity);
  }

  protected abstract BeanConverter<T, E> getConverter();

  protected abstract JpaRepository<E, Long> getRepository();

  protected abstract E updateEntityWithDto(@NonNull E entity, @NonNull T dto);
}
