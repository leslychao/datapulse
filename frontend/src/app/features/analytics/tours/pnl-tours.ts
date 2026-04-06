import { TourDefinition } from '@core/models/tour.model';

export const PNL_OVERVIEW_TOUR: TourDefinition = {
  id: 'pnl-overview',
  titleKey: 'tour.pnl.overview.title',
  triggerOnFirstVisit: true,
  requiredRoute: '/analytics/pnl',
  steps: [
    {
      elementSelector: '[data-tour="analytics-section-tabs"]',
      titleKey: 'tour.pnl.overview.sections.title',
      descriptionKey: 'tour.pnl.overview.sections.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="analytics-sub-nav"]',
      titleKey: 'tour.pnl.overview.subnav.title',
      descriptionKey: 'tour.pnl.overview.subnav.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="pnl-period-filter"]',
      titleKey: 'tour.pnl.overview.period.title',
      descriptionKey: 'tour.pnl.overview.period.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="pnl-kpi-cards"]',
      titleKey: 'tour.pnl.overview.kpi.title',
      descriptionKey: 'tour.pnl.overview.kpi.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="pnl-trend-chart"]',
      titleKey: 'tour.pnl.overview.trend.title',
      descriptionKey: 'tour.pnl.overview.trend.description',
      side: 'top',
    },
    {
      elementSelector: '[data-tour="pnl-cost-breakdown"]',
      titleKey: 'tour.pnl.overview.costs.title',
      descriptionKey: 'tour.pnl.overview.costs.description',
      side: 'left',
    },
  ],
};

export const INVENTORY_BASICS_TOUR: TourDefinition = {
  id: 'inventory-basics',
  titleKey: 'tour.inventory.basics.title',
  triggerOnFirstVisit: false,
  requiredRoute: '/analytics/inventory',
  steps: [
    {
      elementSelector: '[data-tour="analytics-section-tabs"]',
      titleKey: 'tour.inventory.basics.nav.title',
      descriptionKey: 'tour.inventory.basics.nav.description',
      side: 'bottom',
    },
  ],
};
