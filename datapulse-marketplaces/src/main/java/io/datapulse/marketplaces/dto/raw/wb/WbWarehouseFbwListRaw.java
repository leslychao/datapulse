package io.datapulse.marketplaces.dto.raw.wb;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WbWarehouseFbwListRaw {

  @SerializedName("ID")
  private long id;

  private String name;

  private String address;

  private String workTime;

  @SerializedName("acceptsQR")
  private boolean acceptsQR;

  @SerializedName("isActive")
  private boolean active;

  @SerializedName("isTransitActive")
  private boolean transitActive;
}
