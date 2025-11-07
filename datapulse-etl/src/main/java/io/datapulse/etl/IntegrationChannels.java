package io.datapulse.etl;

import lombok.experimental.UtilityClass;

/** Единый словарь имён каналов (без «магических» строк в коде). */
@UtilityClass
public class IntegrationChannels {
  public static final String CHANNEL_HTTP_IN = "CHANNEL_HTTP_IN";
  public static final String CHANNEL_PROCESS_FETCH = "CHANNEL_PROCESS_FETCH";
  public static final String CHANNEL_REACTIVE_STREAM = "CHANNEL_REACTIVE_STREAM";
  public static final String CHANNEL_ETL_INPUT = "CHANNEL_ETL_INPUT";
}
