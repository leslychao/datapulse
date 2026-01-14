import { MetricValue, TableRow } from "../vm/viewModels";

export type DashboardViewModel = {
  metrics: MetricValue[];
  rows: TableRow[];
};

export const mapDashboardDtoToViewModel = (
  _dto: unknown
): DashboardViewModel => {
  return {
    metrics: [],
    rows: []
  };
};
