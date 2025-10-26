package io.datapulse.core.converter;

public interface BeanConverter<T, E> {

  E mapToEntity(T dto);

  T mapToDto(E entity);
}
