import { DataState } from "../vm/dataState";
import { useDashboardState } from "./useDashboardState";

export type DashboardData<T> = {
  state: DataState;
  data: T | null;
};

export const useDashboardData = <T,>(
  requiresConnection = true
): DashboardData<T> => {
  const state = useDashboardState(requiresConnection);

  return {
    state,
    data: null
  };
};
