import { ChartCard } from "../components/ChartCard";
import { FilterBar, FilterField } from "../components/FilterBar";
import { MetricTileGroup } from "../components/MetricTileGroup";
import { DataTableCard } from "../components/DataTableCard";
import { useDashboardData } from "../hooks/useDashboardData";

export const OverviewHome = () => {
  const { state } = useDashboardData(true);

  type TopSkuRow = {
    sku: string;
    revenue: string;
    margin: string;
  };

  const rows: TopSkuRow[] = [];

  const filters: FilterField[] = [
    {
      id: "account",
      label: "Account",
      placeholder: "All accounts",
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
      label: "Date from",
      placeholder: "Start date",
      kind: "date"
    },
    {
      id: "date-to",
      label: "Date to",
      placeholder: "End date",
      kind: "date"
    }
  ];

  return (
    <div className="page">
      <FilterBar fields={filters} />
      <MetricTileGroup
        state={state}
        tiles={[
          { id: "revenue", label: "Revenue", testId: "tile-revenue" },
          { id: "profit", label: "Profit", testId: "tile-profit" },
          { id: "orders", label: "Orders", testId: "tile-orders" },
          { id: "returns", label: "Returns", testId: "tile-returns" }
        ]}
      />
      <div className="grid grid-2">
        <ChartCard title="P&L trend" state={state} />
        <ChartCard title="Orders dynamics" state={state} />
      </div>
      <DataTableCard
        title="Top SKUs"
        state={state}
        columns={[
          { key: "sku", label: "SKU" },
          { key: "revenue", label: "Revenue", sortable: true },
          { key: "margin", label: "Margin", sortable: true }
        ]}
        rows={rows}
        testId="table-top-skus"
      />
    </div>
  );
};
