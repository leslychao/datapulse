import { ChartCard } from "../components/ChartCard";
import { DataTableCard } from "../components/DataTableCard";
import { FilterBar, FilterField } from "../components/FilterBar";
import { MetricTileGroup } from "../components/MetricTileGroup";
import { useDashboardData } from "../hooks/useDashboardData";

export const MarketingAds = () => {
  const { state } = useDashboardData(true);

  type CampaignRow = {
    campaign: string;
    spend: string;
    roi: string;
  };

  const rows: CampaignRow[] = [];

  const filters: FilterField[] = [
    {
      id: "account",
      label: "Account",
      placeholder: "Select account",
      kind: "select"
    },
    {
      id: "marketplace",
      label: "Marketplace",
      placeholder: "All marketplaces",
      kind: "select"
    },
    {
      id: "date-from",
      label: "Period from",
      placeholder: "Start date",
      kind: "date"
    },
    {
      id: "date-to",
      label: "Period to",
      placeholder: "End date",
      kind: "date"
    },
    {
      id: "campaign",
      label: "Campaign",
      placeholder: "All campaigns",
      kind: "select"
    }
  ];

  return (
    <div className="page">
      <FilterBar fields={filters} />
      <MetricTileGroup
        state={state}
        tiles={[
          { id: "spend", label: "Spend", testId: "tile-spend" },
          { id: "impressions", label: "Impressions", testId: "tile-impressions" },
          { id: "clicks", label: "Clicks", testId: "tile-clicks" },
          { id: "drr", label: "DRR / ACoS", testId: "tile-drr" },
          {
            id: "profit-delta",
            label: "Profit delta",
            testId: "tile-profit-delta"
          }
        ]}
      />
      <div className="grid grid-2">
        <DataTableCard
          title="Campaigns"
          state={state}
          compact
          columns={[
            { key: "campaign", label: "Campaign", sortable: true },
            { key: "spend", label: "Spend", sortable: true },
            { key: "roi", label: "ROI", sortable: true }
          ]}
          rows={rows}
          testId="table-campaigns"
        />
        <ChartCard title="Performance correlation" state={state} />
      </div>
    </div>
  );
};
