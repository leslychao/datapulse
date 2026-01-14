export type DataState =
  | "NOT_CONNECTED"
  | "NO_DATA"
  | "UNAVAILABLE"
  | "ERROR"
  | "READY";

export const DATA_STATE_COPY = {
  NOT_CONNECTED: {
    title: "Источник данных не подключён",
    description: "Подключите аккаунт marketplace, чтобы видеть метрики.",
    primaryCtaLabel: "Перейти к подключениям",
    primaryCtaPath: "/settings/connections"
  },
  NO_DATA: {
    title: "Недостаточно данных для построения отчёта",
    description: "Данные ещё не загружены или период пуст.",
    primaryCtaLabel: "Проверить статус загрузки",
    primaryCtaPath: "/data-health/freshness"
  },
  UNAVAILABLE: {
    title: "Раздел в разработке",
    description: "Экран готов. Данные будут подключены после появления API.",
    primaryCtaLabel: "Открыть Data Freshness",
    primaryCtaPath: "/data-health/freshness"
  },
  ERROR: {
    title: "Не удалось загрузить данные",
    description: "Повторите позже или проверьте статус источника.",
    primaryCtaLabel: "Открыть Data Freshness",
    primaryCtaPath: "/data-health/freshness"
  }
} as const;
