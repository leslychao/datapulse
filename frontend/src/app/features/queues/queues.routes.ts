import { Routes } from '@angular/router';

import { QueueItemsPageComponent } from './queue-items-page.component';
import { QueueLayoutComponent } from './queue-layout.component';

const routes: Routes = [
  {
    path: '',
    component: QueueLayoutComponent,
    children: [
      {
        path: ':queueId',
        component: QueueItemsPageComponent,
        data: { breadcrumb: 'breadcrumb.queues.queue' },
      },
    ],
  },
];

export default routes;
