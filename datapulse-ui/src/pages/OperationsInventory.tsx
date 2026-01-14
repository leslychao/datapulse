import { DataTableCard } from "../components/DataTableCard";
import { FilterBar, FilterField } from "../components/FilterBar";
import { MetricTileGroup } from "../components/MetricTileGroup";
import { Tabs } from "../components/Tabs";
import { DataStateGate } from "../components/DataStateGate";
import { useDashboardData } from "../hooks/useDashboardData";

export const OperationsInventory = () => {
  const { state } = useDashboardData(true);

  type InventoryRow = {
    sku: string;
    warehouse: string;
    stock: string;
    doc: string;
  };

  const rows: InventoryRow[] = [];

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
      id: "as-of",
      label: "As of",
      placeholder: "Select date",
      kind: "date"
    },
    {
      id: "warehouse",
      label: "Warehouse",
      placeholder: "All warehouses",
      kind: "select"
    }
  ];

  const tableColumns = [
    { key: "sku", label: "SKU", sortable: true },
    { key: "warehouse", label: "Warehouse", sortable: true },
    { key: "stock", label: "Stock", sortable: true },
    { key: "doc", label: "Days of cover", sortable: true }
  ] as const;

  return (
    <div className="page">
      <FilterBar fields={filters} />
      <MetricTileGroup
        state={state}
        tiles={[
          { id: "out-of-stock", label: "Out of stock", testId: "tile-oos" },
          { id: "low-doc", label: "Low DoC", testId: "tile-low-doc" },
          { id: "excess", label: "Excess stock", testId: "tile-excess" }
        ]}
      />
      <Tabs
        tabs={[
          {
            id: "warehouse",
            label: "By warehouse",
            testId: "tab-warehouse",
            content: (
              <DataTableCard
                title="Inventory by warehouse"
                state={state}
                compact
                columns={[...tableColumns]}
                rows={rows}
                testId="table-inventory-warehouse"
              />
            )
          },
          {
            id: "sku",
            label: "By SKU",
            testId: "tab-sku",
            content: (
              <DataTableCard
                title="Inventory by SKU"
                state={state}
                compact
                columns={[...tableColumns]}
                rows={rows}
                testId="table-inventory-sku"
              />
            )
          }
        ]}
      />
      <div className="card" data-testid="recommendations-panel">
        <div className="card-header">
          <h3 className="card-title">Recommendations</h3>
        </div>
        <div className="chart-placeholder" />
        <DataStateGate state={state} />
      </div>
    </div>
  );
};
