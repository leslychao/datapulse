import { TourGroup } from '@core/models/tour.model';

import { GRID_BASICS_TOUR, GRID_DRAFT_TOUR } from '@features/grid/tours/grid-tours';
import { PNL_OVERVIEW_TOUR, INVENTORY_BASICS_TOUR } from '@features/analytics/tours/pnl-tours';
import {
  PRICING_POLICIES_TOUR,
  PRICING_DECISIONS_TOUR,
  PRICING_LOCKS_TOUR,
} from '@features/pricing/tours/pricing-tours';

export const TOUR_REGISTRY: TourGroup[] = [
  {
    titleKey: 'tour.group.grid',
    tours: [GRID_BASICS_TOUR, GRID_DRAFT_TOUR],
  },
  {
    titleKey: 'tour.group.analytics',
    tours: [PNL_OVERVIEW_TOUR, INVENTORY_BASICS_TOUR],
  },
  {
    titleKey: 'tour.group.pricing',
    tours: [PRICING_POLICIES_TOUR, PRICING_DECISIONS_TOUR, PRICING_LOCKS_TOUR],
  },
];

export function findTourById(id: string) {
  for (const group of TOUR_REGISTRY) {
    const tour = group.tours.find((t) => t.id === id);
    if (tour) return tour;
  }
  return undefined;
}
