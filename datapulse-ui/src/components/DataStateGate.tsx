import { Link } from "react-router-dom";
import { DATA_STATE_COPY, DataState } from "../vm/dataState";

type DataStateGateProps = {
  state: DataState;
  onRetry?: () => void;
  compact?: boolean;
};

export const DataStateGate = ({
  state,
  onRetry,
  compact = false
}: DataStateGateProps) => {
  if (state === "READY") {
    return null;
  }

  const copy = DATA_STATE_COPY[state];

  return (
    <div
      className={`data-state ${compact ? "data-state--compact" : ""}`}
      data-testid="data-state-banner"
    >
      <div>
        <p className="data-state__title">{copy.title}</p>
        <p className="data-state__description">{copy.description}</p>
      </div>
      <div className="data-state__actions">
        {state === "ERROR" && (
          <button
            type="button"
            className="button button--ghost"
            onClick={onRetry}
          >
            Повторить
          </button>
        )}
        <Link className="button button--primary" to={copy.primaryCtaPath}>
          {copy.primaryCtaLabel}
        </Link>
      </div>
    </div>
  );
};
