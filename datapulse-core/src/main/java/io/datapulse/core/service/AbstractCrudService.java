package io.datapulse.core.service;

import static io.datapulse.domain.MessageCodes.DTO_REQUIRED;
import static io.datapulse.domain.MessageCodes.ENTITY_REQUIRED;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@RequiredArgsConstructor
public abstract class AbstractCrudService<D extends LongBaseDto, E extends LongBaseEntity> {

  protected abstract MapperFacade mapper();

  protected abstract JpaRepository<E, Long> repository();

  protected abstract Class<D> dtoType();

  protected abstract Class<E> entityType();

  protected abstract void validateOnCreate(@Valid @NotNull(message = DTO_REQUIRED) D dto);

  public D save(@Valid @NotNull(message = DTO_REQUIRED) D dto) {
    validateOnCreate(dto);
    E entity = mapper().to(dto, entityType());
    E persisted = repository().save(beforeSave(entity));
    return mapper().to(persisted, dtoType());
  }

  public List<D> saveAll(
      @NotEmpty(message = LIST_REQUIRED)
      List<@Valid @NotNull(message = DTO_REQUIRED) D> dtos) {
    return dtos.stream()
        .peek(this::validateOnCreate)
        .map(dto -> mapper().to(dto, entityType()))
        .map(this::beforeSave)
        .collect(Collectors.collectingAndThen(
            Collectors.toList(),
            entities -> repository()
                .saveAll(entities)
                .stream()
                .map(e -> mapper().to(e, dtoType()))
                .toList()
        ));
  }

  public D update(@Valid @NotNull(message = DTO_REQUIRED) D dto) {
    return doUpdate(dto, repository()::save);
  }

  public D updateAndFlush(@Valid @NotNull(message = DTO_REQUIRED) D dto) {
    return doUpdate(dto, repository()::saveAndFlush);
  }

  public List<D> updateAll(@NotEmpty(message = LIST_REQUIRED)
  List<@Valid @NotNull(message = DTO_REQUIRED) D> dtos) {
    return dtos.stream().map(this::updateAndFlush).toList();
  }

  public Optional<D> get(@NotNull(message = ID_REQUIRED) Long id) {
    return repository().findById(id).map(entity -> mapper().to(entity, dtoType()));
  }

  public Page<D> getAllPageable(@NotNull(message = PAGEABLE_REQUIRED) Pageable pageable) {
    return repository().findAll(pageable).map(entity -> mapper().to(entity, dtoType()));
  }

  public List<D> getAll() {
    return repository().findAll().stream().map(entity -> mapper().to(entity, dtoType())).toList();
  }

  public void delete(@NotNull(message = ID_REQUIRED) Long id) {
    try {
      repository().deleteById(id);
    } catch (EmptyResultDataAccessException ex) {
      throw new NotFoundException(NOT_FOUND, id);
    }
  }

  protected abstract void validateOnUpdate(
      @NotNull(message = ID_REQUIRED) Long id,
      @Valid @NotNull(message = DTO_REQUIRED) D dto,
      @NotNull E existing);

  private D doUpdate(
      @Valid @NotNull(message = DTO_REQUIRED) D dto,
      @NotNull Function<E, E> persistFunction) {
    if (dto.getId() == null) {
      throw new AppException(ID_REQUIRED);
    }
    E entity = repository()
        .findById(dto.getId())
        .map(existing -> {
          validateOnUpdate(dto.getId(), dto, existing);
          return merge(existing, dto);
        })
        .map(this::beforeUpdate)
        .map(persistFunction)
        .orElseThrow(() -> new NotFoundException(NOT_FOUND, dto.getId()));
    return mapper().to(entity, dtoType());
  }

  protected E beforeSave(@NotNull(message = ENTITY_REQUIRED) E entity) {
    return entity;
  }

  protected E beforeUpdate(@NotNull(message = ENTITY_REQUIRED) E entity) {
    return entity;
  }

  protected abstract E merge(
      @NotNull(message = ENTITY_REQUIRED) E target,
      @Valid @NotNull(message = DTO_REQUIRED) D source);
}
