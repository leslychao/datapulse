import { ReactNode } from "react";
import { DataState } from "../vm/dataState";
import { DataStateGate } from "./DataStateGate";

type ChartCardProps = {
  title: string;
  state: DataState;
  children?: ReactNode;
};

export const ChartCard = ({ title, state, children }: ChartCardProps) => {
  return (
    <div className="card" data-testid="chart-card">
      <div className="card-header">
        <h3 className="card-title">{title}</h3>
      </div>
      <div className="chart-card__body">
        {children ?? <div className="chart-placeholder" />}
      </div>
      <DataStateGate state={state} />
    </div>
  );
};
