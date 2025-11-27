package io.datapulse.marketplaces.dto.raw.ozon;

import java.util.List;

public record OzonWarehouseListRaw(
    boolean has_entrusted_acceptance,
    boolean is_rfbs,
    String name,
    long warehouse_id,
    boolean can_print_act_in_advance,
    FirstMileType first_mile_type,
    boolean has_postings_limit,
    boolean is_karantin,
    boolean is_kgt,
    boolean is_economy,
    boolean is_timetable_editable,
    int min_postings_limit,
    int postings_limit,
    int min_working_days,
    String status,
    List<String> working_days
) {

  public record FirstMileType(
      String dropoff_point_id,
      int dropoff_timeslot_id,
      boolean first_mile_is_changing,
      String first_mile_type
  ) {

  }
}
