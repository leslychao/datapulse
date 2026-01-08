package io.datapulse.core.service;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.ValidationKeys;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

@RequiredArgsConstructor
public abstract class AbstractCrudService<D extends LongBaseDto, E extends LongBaseEntity> {

  protected abstract MapperFacade mapper();

  protected abstract JpaRepository<E, Long> repository();

  protected abstract Class<D> dtoType();

  protected abstract Class<E> entityType();

  protected abstract void validateOnCreate(
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      D dto
  );

  public D save(
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      D dto
  ) {
    validateOnCreate(dto);
    E entity = mapper().to(dto, entityType());
    E persisted = repository().save(beforeSave(entity));
    return mapper().to(persisted, dtoType());
  }

  public List<D> saveAll(
      @NotEmpty(message = ValidationKeys.LIST_REQUIRED)
      List<@Valid @NotNull(message = ValidationKeys.DTO_REQUIRED) D> dtos
  ) {
    return dtos.stream()
        .peek(this::validateOnCreate)
        .map(dto -> mapper().to(dto, entityType()))
        .map(this::beforeSave)
        .collect(Collectors.collectingAndThen(
            Collectors.toList(),
            entities -> repository()
                .saveAll(entities)
                .stream()
                .map(entity -> mapper().to(entity, dtoType()))
                .toList()
        ));
  }

  public D update(
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      D dto
  ) {
    return doUpdate(dto, repository()::save);
  }

  public D updateAndFlush(
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      D dto
  ) {
    return doUpdate(dto, repository()::saveAndFlush);
  }

  public List<D> updateAll(
      @NotEmpty(message = ValidationKeys.LIST_REQUIRED)
      List<@Valid @NotNull(message = ValidationKeys.DTO_REQUIRED) D> dtos
  ) {
    return dtos.stream().map(this::updateAndFlush).toList();
  }

  public Optional<D> get(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id
  ) {
    return repository().findById(id)
        .map(entity -> mapper().to(entity, dtoType()));
  }

  public Page<D> getAllPageable(
      @NotNull(message = ValidationKeys.PAGEABLE_REQUIRED)
      Pageable pageable
  ) {
    return repository().findAll(pageable)
        .map(entity -> mapper().to(entity, dtoType()));
  }

  public List<D> getAll() {
    return repository().findAll()
        .stream()
        .map(entity -> mapper().to(entity, dtoType()))
        .toList();
  }

  public void delete(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id
  ) {
    if (!repository().existsById(id)) {
      throw new NotFoundException(MessageCodes.NOT_FOUND, id);
    }
    repository().deleteById(id);
    repository().flush();
  }

  protected abstract void validateOnUpdate(
      @NotNull(message = ValidationKeys.ID_REQUIRED)
      Long id,

      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      D dto,

      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      E existing
  );

  private D doUpdate(
      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      D dto,

      @NotNull
      Function<E, E> persistFunction
  ) {
    if (dto.getId() == null) {
      throw new AppException(MessageCodes.ID_REQUIRED);
    }

    E entity = repository()
        .findById(dto.getId())
        .map(existing -> {
          validateOnUpdate(dto.getId(), dto, existing);
          return merge(existing, dto);
        })
        .map(this::beforeUpdate)
        .map(persistFunction)
        .orElseThrow(() -> new NotFoundException(MessageCodes.NOT_FOUND, dto.getId()));

    return mapper().to(entity, dtoType());
  }

  protected E beforeSave(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      E entity
  ) {
    return entity;
  }

  protected E beforeUpdate(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      E entity
  ) {
    return entity;
  }

  protected abstract E merge(
      @NotNull(message = ValidationKeys.ENTITY_REQUIRED)
      E target,

      @Valid
      @NotNull(message = ValidationKeys.DTO_REQUIRED)
      D source
  );
}
