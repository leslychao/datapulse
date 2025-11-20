package io.datapulse.etl.service;

import java.util.List;

public interface EtlPersistService<D> {

  void saveFromEtl(List<D> batch);
}
