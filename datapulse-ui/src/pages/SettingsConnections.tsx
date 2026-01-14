import { DataStateGate } from "../components/DataStateGate";
import { useDashboardData } from "../hooks/useDashboardData";

export const SettingsConnections = () => {
  const { state } = useDashboardData(false);

  return (
    <div className="page">
      <div className="card" data-testid="settings-connections">
        <div className="card-header">
          <h3 className="card-title">Accounts & Connections</h3>
        </div>
        <div className="chart-placeholder" />
        <DataStateGate state={state} />
      </div>
      <div className="card" data-testid="settings-access">
        <div className="card-header">
          <h3 className="card-title">Users & Access</h3>
        </div>
        <div className="chart-placeholder" />
        <DataStateGate state={state} />
      </div>
    </div>
  );
};
