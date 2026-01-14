import { ChartCard } from "../components/ChartCard";
import { DataTableCard } from "../components/DataTableCard";
import { FilterBar, FilterField } from "../components/FilterBar";
import { MetricTileGroup } from "../components/MetricTileGroup";
import { useDashboardData } from "../hooks/useDashboardData";

export const OperationsReturns = () => {
  const { state } = useDashboardData(true);

  type ProblemAreaRow = {
    reason: string;
    share: string;
    loss: string;
  };

  const rows: ProblemAreaRow[] = [];

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
    }
  ];

  return (
    <div className="page">
      <FilterBar fields={filters} />
      <MetricTileGroup
        state={state}
        tiles={[
          { id: "buyout", label: "Buyout %", testId: "tile-buyout" },
          {
            id: "return-loss",
            label: "Return losses",
            testId: "tile-return-loss"
          },
          {
            id: "returns-count",
            label: "Returns count",
            testId: "tile-returns-count"
          },
          {
            id: "pnl-impact",
            label: "P&L impact",
            testId: "tile-pnl-impact"
          }
        ]}
      />
      <div className="grid grid-2">
        <ChartCard title="Returns trend" state={state} />
        <DataTableCard
          title="Problem areas"
          state={state}
          compact
          columns={[
            { key: "reason", label: "Reason", sortable: true },
            { key: "share", label: "Share", sortable: true },
            { key: "loss", label: "Loss impact", sortable: true }
          ]}
          rows={rows}
          testId="table-problem-areas"
        />
      </div>
    </div>
  );
};
