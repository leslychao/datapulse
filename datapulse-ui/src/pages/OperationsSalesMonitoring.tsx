import { DataTableCard } from "../components/DataTableCard";
import { Tabs } from "../components/Tabs";
import { useDashboardData } from "../hooks/useDashboardData";

export const OperationsSalesMonitoring = () => {
  const { state } = useDashboardData(true);

  type SalesRow = {
    order: string;
    sku: string;
    amount: string;
    time: string;
  };

  type OrdersRow = {
    order: string;
    sku: string;
    status: string;
    time: string;
  };

  type ReturnsRow = {
    order: string;
    reason: string;
    amount: string;
    time: string;
  };

  const salesRows: SalesRow[] = [];
  const ordersRows: OrdersRow[] = [];
  const returnsRows: ReturnsRow[] = [];

  return (
    <div className="page">
      <div className="inline-info" data-testid="last-refresh">
        <span className="inline-info__title">Last refresh</span>
        <span className="inline-info__value">Refresh unavailable</span>
      </div>
      <div className="tabs__list" role="tablist">
        <button type="button" className="tabs__tab is-active">
          Today
        </button>
        <button type="button" className="tabs__tab">
          Last 60 min
        </button>
        <button type="button" className="tabs__tab">
          Last 24h
        </button>
      </div>
      <Tabs
        tabs={[
          {
            id: "sales",
            label: "Sales",
            testId: "tab-sales",
            content: (
              <DataTableCard
                title="Sales stream"
                state={state}
                compact
                columns={[
                  { key: "order", label: "Order", sortable: true },
                  { key: "sku", label: "SKU", sortable: true },
                  { key: "amount", label: "Amount", sortable: true },
                  { key: "time", label: "Time", sortable: true }
                ]}
                rows={salesRows}
                testId="table-sales"
              />
            )
          },
          {
            id: "orders",
            label: "Orders",
            testId: "tab-orders",
            content: (
              <DataTableCard
                title="Orders stream"
                state={state}
                compact
                columns={[
                  { key: "order", label: "Order", sortable: true },
                  { key: "sku", label: "SKU", sortable: true },
                  { key: "status", label: "Status", sortable: true },
                  { key: "time", label: "Time", sortable: true }
                ]}
                rows={ordersRows}
                testId="table-orders"
              />
            )
          },
          {
            id: "returns",
            label: "Returns",
            testId: "tab-returns",
            content: (
              <DataTableCard
                title="Returns stream"
                state={state}
                compact
                columns={[
                  { key: "order", label: "Order", sortable: true },
                  { key: "reason", label: "Reason", sortable: true },
                  { key: "amount", label: "Amount", sortable: true },
                  { key: "time", label: "Time", sortable: true }
                ]}
                rows={returnsRows}
                testId="table-returns"
              />
            )
          }
        ]}
      />
    </div>
  );
};
