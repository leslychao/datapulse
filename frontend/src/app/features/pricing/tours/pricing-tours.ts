import { TourDefinition } from '@core/models/tour.model';

export const PRICING_POLICIES_TOUR: TourDefinition = {
  id: 'pricing-policies',
  titleKey: 'tour.pricing.policies.title',
  triggerOnFirstVisit: true,
  requiredRoute: '/pricing/policies',
  steps: [
    {
      elementSelector: '[data-tour="pricing-tabs"]',
      titleKey: 'tour.pricing.policies.tabs.title',
      descriptionKey: 'tour.pricing.policies.tabs.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="policy-create-btn"]',
      titleKey: 'tour.pricing.policies.create.title',
      descriptionKey: 'tour.pricing.policies.create.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="policy-filter-bar"]',
      titleKey: 'tour.pricing.policies.filters.title',
      descriptionKey: 'tour.pricing.policies.filters.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="policy-table"]',
      titleKey: 'tour.pricing.policies.table.title',
      descriptionKey: 'tour.pricing.policies.table.description',
      side: 'top',
    },
  ],
};

export const PRICING_DECISIONS_TOUR: TourDefinition = {
  id: 'pricing-decisions',
  titleKey: 'tour.pricing.decisions.title',
  triggerOnFirstVisit: false,
  requiredRoute: '/pricing/decisions',
  steps: [
    {
      elementSelector: '[data-tour="decisions-filter-bar"]',
      titleKey: 'tour.pricing.decisions.filters.title',
      descriptionKey: 'tour.pricing.decisions.filters.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="decisions-grid"]',
      titleKey: 'tour.pricing.decisions.grid.title',
      descriptionKey: 'tour.pricing.decisions.grid.description',
      side: 'top',
    },
  ],
};

export const PRICING_LOCKS_TOUR: TourDefinition = {
  id: 'pricing-locks',
  titleKey: 'tour.pricing.locks.title',
  triggerOnFirstVisit: false,
  requiredRoute: '/pricing/locks',
  steps: [
    {
      elementSelector: '[data-tour="locks-create-btn"]',
      titleKey: 'tour.pricing.locks.create_btn.title',
      descriptionKey: 'tour.pricing.locks.create_btn.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="locks-grid"]',
      titleKey: 'tour.pricing.locks.grid.title',
      descriptionKey: 'tour.pricing.locks.grid.description',
      side: 'top',
    },
  ],
};
