import type { Side } from 'driver.js';

export interface TourStep {
  elementSelector: string;
  titleKey: string;
  descriptionKey: string;
  side?: Side;
  highlightPadding?: number;
}

export interface TourDefinition {
  id: string;
  titleKey: string;
  steps: TourStep[];
  triggerOnFirstVisit: boolean;
  requiredRoute: string;
}

export interface TourGroup {
  titleKey: string;
  tours: TourDefinition[];
}
