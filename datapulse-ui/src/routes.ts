export type NavItem = {
  label: string;
  path: string;
  testId: string;
};

export type NavSection = {
  title: string;
  items: NavItem[];
};

export const navSections: NavSection[] = [
  {
    title: "Overview",
    items: [
      {
        label: "Home / Summary",
        path: "/",
        testId: "nav-overview-home"
      }
    ]
  },
  {
    title: "Finance",
    items: [
      {
        label: "P&L (Account-level)",
        path: "/finance/pnl",
        testId: "nav-finance-pnl"
      },
      {
        label: "Unit Economics (SKU)",
        path: "/finance/unit-economics",
        testId: "nav-finance-unit-economics"
      }
    ]
  },
  {
    title: "Operations",
    items: [
      {
        label: "Inventory & DoC",
        path: "/operations/inventory",
        testId: "nav-operations-inventory"
      },
      {
        label: "Returns & Buyout",
        path: "/operations/returns",
        testId: "nav-operations-returns"
      },
      {
        label: "Sales / Orders Monitoring",
        path: "/operations/sales-monitoring",
        testId: "nav-operations-sales-monitoring"
      }
    ]
  },
  {
    title: "Marketing",
    items: [
      {
        label: "Ads / Marketing",
        path: "/marketing/ads",
        testId: "nav-marketing-ads"
      }
    ]
  },
  {
    title: "Data Health",
    items: [
      {
        label: "Data Freshness / SLA",
        path: "/data-health/freshness",
        testId: "nav-data-health-freshness"
      }
    ]
  },
  {
    title: "Settings",
    items: [
      {
        label: "Accounts & Connections",
        path: "/settings/connections",
        testId: "nav-settings-connections"
      }
    ]
  }
];

export const routeLabels = navSections
  .flatMap((section) => section.items)
  .reduce<Record<string, string>>((acc, item) => {
    acc[item.path] = item.label;
    return acc;
  }, {});
