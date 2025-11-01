package io.datapulse.core.service;

import io.datapulse.core.entity.LongBaseEntity;
import io.datapulse.domain.dto.LongBaseDto;

public abstract class AbstractIngestApiService<CR, UR, R, D extends LongBaseDto, E extends LongBaseEntity> extends
    AbstractCrudService<D, E> {

  protected abstract Class<R> responseClass();

}
