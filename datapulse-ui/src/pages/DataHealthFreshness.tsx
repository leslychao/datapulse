import { DataTableCard } from "../components/DataTableCard";
import { MetricTileGroup } from "../components/MetricTileGroup";
import { useDashboardData } from "../hooks/useDashboardData";
import { DataStateGate } from "../components/DataStateGate";

export const DataHealthFreshness = () => {
  const { state } = useDashboardData(false);

  type FreshnessRow = {
    entity: string;
    lastUpdate: string;
    status: string;
    lag: string;
  };

  const rows: FreshnessRow[] = [];

  return (
    <div className="page">
      <MetricTileGroup
        state={state}
        tiles={[
          { id: "raw", label: "RAW updated", testId: "tile-raw-updated" },
          { id: "marts", label: "Marts updated", testId: "tile-marts-updated" },
          { id: "sla", label: "SLA breaches", testId: "tile-sla-breaches" },
          { id: "quality", label: "Quality checks", testId: "tile-quality" }
        ]}
      />
      <DataTableCard
        title="Entity freshness"
        state={state}
        columns={[
          { key: "entity", label: "Entity", sortable: true },
          { key: "lastUpdate", label: "Last update", sortable: true },
          { key: "status", label: "Status", sortable: true },
          { key: "lag", label: "Lag duration", sortable: true }
        ]}
        rows={rows}
        testId="table-freshness"
      />
      <div className="card" data-testid="alerts-list">
        <div className="card-header">
          <h3 className="card-title">Alerts</h3>
        </div>
        <div className="chart-placeholder" />
        <DataStateGate state={state} />
      </div>
    </div>
  );
};
