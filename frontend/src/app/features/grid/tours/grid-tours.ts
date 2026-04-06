import { TourDefinition } from '@core/models/tour.model';

export const GRID_BASICS_TOUR: TourDefinition = {
  id: 'grid-basics',
  titleKey: 'tour.grid.basics.title',
  triggerOnFirstVisit: true,
  requiredRoute: '/grid',
  steps: [
    {
      elementSelector: '[data-tour="grid-kpi-strip"]',
      titleKey: 'tour.grid.basics.kpi.title',
      descriptionKey: 'tour.grid.basics.kpi.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="grid-view-tabs"]',
      titleKey: 'tour.grid.basics.views.title',
      descriptionKey: 'tour.grid.basics.views.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="grid-search"]',
      titleKey: 'tour.grid.basics.search.title',
      descriptionKey: 'tour.grid.basics.search.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="grid-filters"]',
      titleKey: 'tour.grid.basics.filters.title',
      descriptionKey: 'tour.grid.basics.filters.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="grid-table"]',
      titleKey: 'tour.grid.basics.table.title',
      descriptionKey: 'tour.grid.basics.table.description',
      side: 'top',
    },
    {
      elementSelector: '[data-tour="grid-export"]',
      titleKey: 'tour.grid.basics.export.title',
      descriptionKey: 'tour.grid.basics.export.description',
      side: 'bottom',
    },
  ],
};

export const GRID_DRAFT_TOUR: TourDefinition = {
  id: 'grid-draft',
  titleKey: 'tour.grid.draft.title',
  triggerOnFirstVisit: false,
  requiredRoute: '/grid',
  steps: [
    {
      elementSelector: '[data-tour="grid-draft-toggle"]',
      titleKey: 'tour.grid.draft.toggle.title',
      descriptionKey: 'tour.grid.draft.toggle.description',
      side: 'bottom',
    },
    {
      elementSelector: '[data-tour="grid-table"]',
      titleKey: 'tour.grid.draft.editing.title',
      descriptionKey: 'tour.grid.draft.editing.description',
      side: 'top',
    },
  ],
};
