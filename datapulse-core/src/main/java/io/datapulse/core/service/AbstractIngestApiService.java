package io.datapulse.core.service;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.core.mapper.MapperFacade;
import io.datapulse.domain.dto.LongBaseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Базовый сервис ингеста (Ingest) для create/update API-флоу.
 *
 * @param <CreateReq> тип запроса на создание
 * @param <UpdateReq> тип запроса на обновление
 * @param <Resp>      тип ответа API
 * @param <D>         тип DTO (должен иметь id типа Long)
 * @param <E>         тип JPA-сущности (с Long id)
 */
public abstract class AbstractIngestApiService<CreateReq, UpdateReq, Resp,
    D extends LongBaseDto, E extends LongBaseEntity>
    extends AbstractCrudService<D, E> {

  private final MapperFacade mapperFacade;

  protected AbstractIngestApiService(MapperFacade mapperFacade) {
    super(mapperFacade); // если родитель ожидает именно MapperFacade
    this.mapperFacade = mapperFacade;
  }

  protected final MapperFacade mapper() {
    return mapperFacade;
  }

  protected abstract Class<D> dtoType();

  protected abstract Class<E> entityType();

  protected abstract Class<Resp> responseType();

  @Override
  protected abstract E merge(@NotNull E target, @Valid @NotNull D source);

  /**
   * Ингест из запроса на создание (create).
   */
  public final Resp saveFromRequest(@Valid @NotNull CreateReq request) {
    D dto = mapper().to(request, dtoType());
    D saved = save(dto);
    return mapper().to(saved, responseType());
  }

  /**
   * Ингест из запроса на обновление (update). По умолчанию маппит UpdateReq → D и вызывает
   * стандартный save(D), который внутри выполнит merge() с существующей сущностью. При
   * необходимости можно переопределить toDtoFromUpdate(UpdateReq).
   */
  public final Resp saveFromUpdateRequest(@Valid @NotNull UpdateReq request) {
    D dto = toDtoFromUpdate(request);
    D saved = save(dto);
    return mapper().to(saved, responseType());
  }

  /**
   * Маппинг UpdateReq → DTO. Можно переопределить в наследниках, если обновление требует
   * специальной логики (частичные апдейты и т.п.).
   */
  protected D toDtoFromUpdate(@NotNull UpdateReq request) {
    return mapper().to(request, dtoType());
  }
}
