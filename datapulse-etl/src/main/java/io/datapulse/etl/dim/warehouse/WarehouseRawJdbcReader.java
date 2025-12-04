package io.datapulse.etl.dim.warehouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datapulse.domain.MarketplaceType;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import io.datapulse.etl.RawTableNames;
import io.datapulse.marketplaces.dto.raw.ozon.OzonClusterListRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonClusterListRaw.OzonWarehouseRaw;
import io.datapulse.marketplaces.dto.raw.ozon.OzonWarehouseFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbOfficeFbsListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseFbwListRaw;
import io.datapulse.marketplaces.dto.raw.wb.WbWarehouseSellerListRaw;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WarehouseRawJdbcReader implements WarehouseRawReader {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public Stream<OzonWarehouseFbsListRaw> streamOzonFbs(Long accountId, String requestId) {
    return readRawRows(
        RawTableNames.RAW_OZON_WAREHOUSES_FBS,
        MarketplaceType.OZON,
        accountId,
        requestId,
        OzonWarehouseFbsListRaw.class
    );
  }

  @Override
  public Stream<OzonWarehouseRaw> streamOzonFboWarehouses(Long accountId, String requestId) {
    return readRawRows(
        RawTableNames.RAW_OZON_WAREHOUSES_FBO,
        MarketplaceType.OZON,
        accountId,
        requestId,
        OzonClusterListRaw.class
    )
        .flatMap(this::toOzonWarehousesStream);
  }

  @Override
  public Stream<WbWarehouseFbwListRaw> streamWbFbw(Long accountId, String requestId) {
    return readRawRows(
        RawTableNames.RAW_WB_WAREHOUSES_FBW,
        MarketplaceType.WILDBERRIES,
        accountId,
        requestId,
        WbWarehouseFbwListRaw.class
    );
  }

  @Override
  public Stream<WbOfficeFbsListRaw> streamWbFbsOffices(Long accountId, String requestId) {
    return readRawRows(
        RawTableNames.RAW_WB_OFFICES_FBS,
        MarketplaceType.WILDBERRIES,
        accountId,
        requestId,
        WbOfficeFbsListRaw.class
    );
  }

  @Override
  public Stream<WbWarehouseSellerListRaw> streamWbSellerWarehouses(Long accountId,
      String requestId) {
    return readRawRows(
        RawTableNames.RAW_WB_WAREHOUSES_SELLER,
        MarketplaceType.WILDBERRIES,
        accountId,
        requestId,
        WbWarehouseSellerListRaw.class
    );
  }

  private boolean tableExists(String table) {
    Boolean exists = jdbcTemplate.queryForObject(
        "select exists (select 1 from information_schema.tables where table_name = ?)",
        Boolean.class,
        table
    );
    return Boolean.TRUE.equals(exists);
  }

  private <T> Stream<T> readRawRows(
      String table,
      MarketplaceType marketplace,
      Long accountId,
      String requestId,
      Class<T> clazz
  ) {
    if (!tableExists(table)) {
      return Stream.empty();
    }
    return jdbcTemplate.queryForStream(
        """
            select payload
            from %s
            where account_id = ? and request_id = ? and marketplace = ?
            order by id
            """.formatted(table),
        (rs, rowNum) -> deserialize(rs.getString("payload"), clazz),
        accountId,
        requestId,
        marketplace.name()
    );
  }

  private Stream<OzonWarehouseRaw> toOzonWarehousesStream(OzonClusterListRaw cluster) {
    if (cluster == null || cluster.logistic_clusters() == null) {
      return Stream.empty();
    }

    return cluster.logistic_clusters()
        .stream()
        .filter(it -> it != null && it.warehouses() != null)
        .flatMap(it -> it.warehouses().stream());
  }

  private <T> T deserialize(String payload, Class<T> clazz) {
    try {
      return objectMapper.readValue(payload, clazz);
    } catch (JsonProcessingException ex) {
      throw new AppException(MessageCodes.SERIALIZATION_ERROR, ex);
    }
  }

}
