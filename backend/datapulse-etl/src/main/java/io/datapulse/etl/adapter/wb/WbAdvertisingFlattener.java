package io.datapulse.etl.adapter.wb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import io.datapulse.etl.adapter.wb.dto.WbFullstatsCampaignDto;
import io.datapulse.etl.persistence.clickhouse.AdvertisingFactRow;
import org.springframework.stereotype.Service;

/**
 * Transforms WB fullstats v3 hierarchical JSON
 * (campaign → days → apps → nms) into flat {@link AdvertisingFactRow} list.
 *
 * <p>CTR and CPC are computed from raw values (not taken from API response):
 * {@code ctr = clicks / views}, {@code cpc = spend / clicks}.</p>
 */
@Service
public class WbAdvertisingFlattener {

  private static final String SOURCE_PLATFORM = "WB";

  public List<AdvertisingFactRow> flatten(
      List<WbFullstatsCampaignDto> campaigns,
      long connectionId,
      long jobExecutionId) {

    List<AdvertisingFactRow> rows = new ArrayList<>();

    for (var campaign : campaigns) {
      if (campaign.days() == null) {
        continue;
      }
      for (var day : campaign.days()) {
        LocalDate adDate = LocalDate.parse(day.date());
        if (day.apps() == null) {
          continue;
        }
        for (var app : day.apps()) {
          if (app.nms() == null) {
            continue;
          }
          for (var nm : app.nms()) {
            rows.add(toRow(campaign.advertId(), adDate, nm,
                connectionId, jobExecutionId));
          }
        }
      }
    }

    return rows;
  }

  private AdvertisingFactRow toRow(long campaignId, LocalDate adDate,
      WbFullstatsCampaignDto.Nm nm,
      long connectionId, long jobExecutionId) {

    float ctr = nm.views() > 0
        ? (float) nm.clicks() / nm.views()
        : 0f;
    BigDecimal cpc = nm.clicks() > 0
        ? nm.sum().divide(
            BigDecimal.valueOf(nm.clicks()), 2, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;

    return new AdvertisingFactRow(
        connectionId,
        SOURCE_PLATFORM,
        campaignId,
        adDate,
        String.valueOf(nm.nmId()),
        nm.views(),
        nm.clicks(),
        nm.sum(),
        nm.orders(),
        nm.shks(),
        nm.sumPrice(),
        nm.canceled(),
        ctr,
        cpc,
        (float) nm.cr(),
        jobExecutionId
    );
  }
}
