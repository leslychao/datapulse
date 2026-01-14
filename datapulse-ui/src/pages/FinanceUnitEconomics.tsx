import { DataTableCard } from "../components/DataTableCard";
import { FilterBar, FilterField } from "../components/FilterBar";
import { MetricTileGroup } from "../components/MetricTileGroup";
import { useDashboardData } from "../hooks/useDashboardData";

export const FinanceUnitEconomics = () => {
  const { state } = useDashboardData(true);

  type UnitEconomicsRow = {
    sku: string;
    units: string;
    margin: string;
  };

  const rows: UnitEconomicsRow[] = [];

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
      id: "search",
      label: "Search",
      placeholder: "SKU or product",
      kind: "search"
    }
  ];

  return (
    <div className="page">
      <FilterBar fields={filters} />
      <MetricTileGroup
        state={state}
        tiles={[
          {
            id: "avg-margin",
            label: "Avg margin / unit",
            testId: "tile-avg-margin",
            tone: "profit"
          },
          {
            id: "top-loss",
            label: "Top loss SKUs",
            testId: "tile-top-loss",
            tone: "loss"
          }
        ]}
      />
      <DataTableCard
        title="SKU Unit Economics"
        state={state}
        compact
        columns={[
          { key: "sku", label: "SKU", sortable: true },
          { key: "units", label: "Units", sortable: true },
          { key: "margin", label: "Margin / unit", sortable: true }
        ]}
        rows={rows}
        testId="table-unit-economics"
      />
    </div>
  );
};
