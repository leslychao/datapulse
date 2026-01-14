import { useMemo, useState } from "react";
import { DataState } from "../vm/dataState";
import { DataStateGate } from "./DataStateGate";

export type TableColumn<T> = {
  key: keyof T;
  label: string;
  sortable?: boolean;
};

type DataTableCardProps<T> = {
  title: string;
  columns: TableColumn<T>[];
  rows: T[];
  state: DataState;
  compact?: boolean;
  testId?: string;
};

export const DataTableCard = <T extends Record<string, string>>({
  title,
  columns,
  rows,
  state,
  compact = false,
  testId
}: DataTableCardProps<T>) => {
  const [sortKey, setSortKey] = useState<keyof T | null>(null);
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");

  const sortedRows = useMemo(() => {
    if (!sortKey) {
      return rows;
    }
    return [...rows].sort((a, b) => {
      const left = a[sortKey] ?? "";
      const right = b[sortKey] ?? "";
      if (left === right) {
        return 0;
      }
      const order = left > right ? 1 : -1;
      return sortDirection === "asc" ? order : -order;
    });
  }, [rows, sortKey, sortDirection]);

  const handleSort = (key: keyof T) => {
    if (sortKey === key) {
      setSortDirection((current) => (current === "asc" ? "desc" : "asc"));
      return;
    }
    setSortKey(key);
    setSortDirection("asc");
  };

  return (
    <div className="card" data-testid={testId ?? "data-table-card"}>
      <div className="card-header">
        <h3 className="card-title">{title}</h3>
        <span className="muted">{rows.length} rows</span>
      </div>
      <div
        className={`table-wrapper ${compact ? "table-wrapper--compact" : ""}`}
      >
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={String(column.key)}
                  className={column.sortable ? "sortable" : undefined}
                  onClick={
                    column.sortable ? () => handleSort(column.key) : undefined
                  }
                >
                  <span>{column.label}</span>
                  {column.sortable && sortKey === column.key && (
                    <span className="sort-indicator">
                      {sortDirection === "asc" ? "↑" : "↓"}
                    </span>
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedRows.map((row, index) => (
              <tr key={index}>
                {columns.map((column) => (
                  <td key={String(column.key)}>{row[column.key]}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <DataStateGate state={state} />
    </div>
  );
};
