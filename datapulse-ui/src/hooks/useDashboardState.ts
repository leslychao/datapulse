import { useConnectionStatus } from "./useConnectionStatus";
import { DataState } from "../vm/dataState";

export const useDashboardState = (requiresConnection = true): DataState => {
  const connectionStatus = useConnectionStatus();

  if (requiresConnection && connectionStatus === "not_connected") {
    return "NOT_CONNECTED";
  }

  return "UNAVAILABLE";
};
