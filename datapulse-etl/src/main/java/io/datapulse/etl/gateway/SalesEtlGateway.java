package io.datapulse.etl.gateway;
    
    import org.springframework.integration.annotation.Gateway;
    import org.springframework.integration.annotation.MessagingGateway;
    
    import static io.datapulse.core.integration.IntegrationConstants.*;
    
    @MessagingGateway
    public interface SalesEtlGateway {
      @Gateway(requestChannel = CHANNEL_SALES_CRON)
      void triggerSalesSync();
    }
