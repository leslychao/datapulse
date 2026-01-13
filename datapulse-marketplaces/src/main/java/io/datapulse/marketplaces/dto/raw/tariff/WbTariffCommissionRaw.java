package io.datapulse.marketplaces.dto.raw.tariff;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WbTariffCommissionRaw {

  private double kgvpBooking;

  private double kgvpMarketplace;

  private double kgvpPickup;

  private double kgvpSupplier;

  private double kgvpSupplierExpress;

  private double paidStorageKgvp;

  @SerializedName("parentID")
  @JsonProperty("parentID")
  private long parentId;

  private String parentName;

  @SerializedName("subjectID")
  @JsonProperty("subjectID")
  private long subjectId;

  private String subjectName;
}
