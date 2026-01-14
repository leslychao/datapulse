export type MetricValue = {
  id: string;
  label: string;
  value?: string;
  tone?: "profit" | "loss" | "neutral";
};

export type TableRow = Record<string, string>;
