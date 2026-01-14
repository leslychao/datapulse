import { DataState } from "../vm/dataState";

export type MetricTile = {
  id: string;
  label: string;
  value?: string;
  tone?: "profit" | "loss" | "neutral";
  testId?: string;
};

type MetricTileGroupProps = {
  tiles: MetricTile[];
  state: DataState;
};

export const MetricTileGroup = ({ tiles, state }: MetricTileGroupProps) => {
  const showSkeleton = state !== "READY";

  return (
    <div className="metric-grid" data-testid="metric-tile-group">
      {tiles.map((tile) => (
        <div className="metric-tile" key={tile.id} data-testid={tile.testId}>
          <div className="metric-tile__label">{tile.label}</div>
          <div
            className={`metric-tile__value ${
              tile.tone && !showSkeleton ? `metric-tile__value--${tile.tone}` : ""
            }`}
          >
            {showSkeleton ? <div className="skeleton" /> : tile.value ?? "—"}
          </div>
        </div>
      ))}
    </div>
  );
};
