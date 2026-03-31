package io.datapulse.etl.domain;

import java.util.Map;

import io.datapulse.etl.domain.FinanceEntryType.MeasureColumn;
import lombok.extern.slf4j.Slf4j;

/**
 * Map-based classifier for Ozon {@code services[].name} values.
 * Maps each known service name to its canonical {@link MeasureColumn}.
 *
 * <p>Based on empirically verified service names from mapping-spec.md §7.
 * Unknown services default to {@link MeasureColumn#OTHER} with a warning log.</p>
 */
@Slf4j
public final class OzonServiceClassifier {

    private OzonServiceClassifier() {
    }

    private static final Map<String, MeasureColumn> SERVICE_LOOKUP = Map.ofEntries(
            // Logistics (8 known services)
            Map.entry("MarketplaceServiceItemDirectFlowLogistic", MeasureColumn.LOGISTICS),
            Map.entry("MarketplaceServiceItemDelivToCustomer", MeasureColumn.LOGISTICS),
            Map.entry("MarketplaceServiceItemReturnFlowLogistic", MeasureColumn.LOGISTICS),
            Map.entry("MarketplaceServiceItemReturnNotDelivToCustomer", MeasureColumn.LOGISTICS),
            Map.entry("MarketplaceServiceItemReturnAfterDelivToCustomer", MeasureColumn.LOGISTICS),
            Map.entry("MarketplaceServiceItemRedistributionReturnsPVZ", MeasureColumn.LOGISTICS),
            Map.entry("MarketplaceServiceItemRedistributionDropOffApvz", MeasureColumn.LOGISTICS),
            Map.entry("MarketplaceServiceItemDropoffPVZ", MeasureColumn.LOGISTICS),

            // Acquiring (embedded in sale operation services[])
            Map.entry("MarketplaceRedistributionOfAcquiringOperation", MeasureColumn.ACQUIRING),

            // Brand commission (embedded in sale operation services[])
            Map.entry("MarketplaceServiceBrandCommission", MeasureColumn.MARKETPLACE_COMMISSION),

            // Subscription / stars membership
            Map.entry("ItemAgentServiceStarsMembership", MeasureColumn.OTHER),

            // Disposal (penalty-like)
            Map.entry("MarketplaceServiceItemDisposalDetailed", MeasureColumn.PENALTIES)
    );

    /**
     * Classifies an Ozon service name into the canonical measure column.
     * Falls back to {@link MeasureColumn#OTHER} for unrecognized service names
     * and logs a warning so new services can be added to the taxonomy.
     */
    public static MeasureColumn classify(String serviceName) {
        if (serviceName == null) {
            return MeasureColumn.OTHER;
        }
        MeasureColumn column = SERVICE_LOOKUP.get(serviceName);
        if (column != null) {
            return column;
        }
        log.warn("Unmapped Ozon service name: serviceName={}", serviceName);
        return MeasureColumn.OTHER;
    }
}
