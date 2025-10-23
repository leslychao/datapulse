package io.datapulse.core.integration;
    
    public final class IntegrationConstants {
      private IntegrationConstants() {}
    
      // Каналы
      public static final String CHANNEL_SALES_CRON = "CHANNEL_SALES_CRON";
      public static final String CHANNEL_ROUTE_ACCOUNTS = "CHANNEL_ROUTE_ACCOUNTS";
      public static final String CHANNEL_FETCH_SALES = "CHANNEL_FETCH_SALES";
      public static final String CHANNEL_NORMALIZE_SALES = "CHANNEL_NORMALIZE_SALES";
      public static final String CHANNEL_PERSIST_SALES = "CHANNEL_PERSIST_SALES";
      public static final String CHANNEL_PROGRESS = "CHANNEL_PROGRESS";
      public static final String CHANNEL_ERRORS = "CHANNEL_ERRORS";
      public static final String CHANNEL_ANALYTICS_REFRESH = "CHANNEL_ANALYTICS_REFRESH";
    
      // Заголовки
      public static final String HEADER_ACCOUNT_ID = "HEADER_ACCOUNT_ID";
      public static final String HEADER_JOB_ID = "HEADER_JOB_ID";
    }
