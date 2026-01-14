export type ConnectionStatus = "connected" | "not_connected";

export const useConnectionStatus = (): ConnectionStatus => {
  return "not_connected";
};
