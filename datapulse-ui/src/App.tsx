import { Navigate, Route, Routes } from "react-router-dom";
import { DashboardShell } from "./components/DashboardShell";
import { OverviewHome } from "./pages/OverviewHome";
import { FinancePnl } from "./pages/FinancePnl";
import { FinanceUnitEconomics } from "./pages/FinanceUnitEconomics";
import { OperationsInventory } from "./pages/OperationsInventory";
import { OperationsReturns } from "./pages/OperationsReturns";
import { OperationsSalesMonitoring } from "./pages/OperationsSalesMonitoring";
import { MarketingAds } from "./pages/MarketingAds";
import { DataHealthFreshness } from "./pages/DataHealthFreshness";
import { SettingsConnections } from "./pages/SettingsConnections";

const App = () => {
  return (
    <Routes>
      <Route element={<DashboardShell />}>
        <Route path="/" element={<OverviewHome />} />
        <Route path="/finance/pnl" element={<FinancePnl />} />
        <Route
          path="/finance/unit-economics"
          element={<FinanceUnitEconomics />}
        />
        <Route path="/operations/inventory" element={<OperationsInventory />} />
        <Route path="/operations/returns" element={<OperationsReturns />} />
        <Route
          path="/operations/sales-monitoring"
          element={<OperationsSalesMonitoring />}
        />
        <Route path="/marketing/ads" element={<MarketingAds />} />
        <Route path="/data-health/freshness" element={<DataHealthFreshness />} />
        <Route path="/settings/connections" element={<SettingsConnections />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
};

export default App;
