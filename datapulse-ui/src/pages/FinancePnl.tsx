import { ChartCard } from "../components/ChartCard";
import { FilterBar, FilterField } from "../components/FilterBar";
import { MetricTileGroup } from "../components/MetricTileGroup";
import { useDashboardData } from "../hooks/useDashboardData";

export const FinancePnl = () => {
  const { state } = useDashboardData(true);

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
      id: "granularity",
      label: "Granularity",
      placeholder: "Day / Week / Month",
      kind: "select"
    }
  ];

  return (
    <div className="page">
      <FilterBar fields={filters} />
      <MetricTileGroup
        state={state}
        tiles={[
          { id: "revenue", label: "Revenue", testId: "tile-revenue" },
          { id: "commissions", label: "Commissions", testId: "tile-commissions" },
          { id: "logistics", label: "Logistics", testId: "tile-logistics" },
          { id: "ads", label: "Ads", testId: "tile-ads" },
          { id: "penalties", label: "Penalties", testId: "tile-penalties" },
          { id: "returns", label: "Returns", testId: "tile-returns" },
          { id: "net-payout", label: "Net payout", testId: "tile-net-payout" },
          {
            id: "profit",
            label: "Profit",
            testId: "tile-profit",
            tone: "profit"
          }
        ]}
      />
      <div className="grid grid-2">
        <ChartCard title="Dynamics by period" state={state} />
        <ChartCard title="Composition / Waterfall" state={state} />
      </div>
    </div>
  );
};
