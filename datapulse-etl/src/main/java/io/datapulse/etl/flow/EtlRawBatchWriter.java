package io.datapulse.etl.flow;

import static io.datapulse.etl.EtlFlowHeaders.HDR_MARKETPLACE;
import static io.datapulse.etl.EtlFlowHeaders.HDR_RAW_TABLE;
import static io.datapulse.etl.EtlFlowHeaders.HDR_WORK;

import io.datapulse.domain.MarketplaceType;
import io.datapulse.etl.dto.EtlSourceExecution;
import io.datapulse.etl.repository.jdbc.RawBatchInsertJdbcRepository;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EtlRawBatchWriter {

  private final RawBatchInsertJdbcRepository rawRepo;

  public EtlRawBatchWriter(RawBatchInsertJdbcRepository rawRepo) {
    this.rawRepo = rawRepo;
  }

  @Transactional
  public RawBatchEnvelope write(Message<RawBatchEnvelope> msg) {
    RawBatchEnvelope env = msg.getPayload();
    if (env == null || env.batch() == null || env.batch().isEmpty()) {
      return env;
    }

    EtlSourceExecution work = (EtlSourceExecution) msg.getHeaders().get(HDR_WORK);
    String table = (String) msg.getHeaders().get(HDR_RAW_TABLE);
    MarketplaceType marketplace = (MarketplaceType) msg.getHeaders().get(HDR_MARKETPLACE);

    rawRepo.saveBatch((java.util.List) env.batch(), table, work.requestId(), work.accountId(), marketplace);
    return env;
  }
}