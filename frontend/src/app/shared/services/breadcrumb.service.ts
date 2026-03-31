import { Injectable, inject, signal } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { filter } from 'rxjs/operators';

export interface BreadcrumbSegment {
  label: string;
  route: string | null;
}

@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);

  readonly segments = signal<BreadcrumbSegment[]>([]);

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.buildFromRoute());
  }

  setSegments(segments: BreadcrumbSegment[]): void {
    this.segments.set(segments);
  }

  private buildFromRoute(): void {
    const segments: BreadcrumbSegment[] = [];
    let route: ActivatedRoute | null = this.activatedRoute.root;
    let url = '';

    while (route) {
      if (route.snapshot.data['breadcrumb']) {
        const pathSegments = route.snapshot.url.map((seg) => seg.path);
        if (pathSegments.length > 0) {
          url += '/' + pathSegments.join('/');
        }
        segments.push({
          label: route.snapshot.data['breadcrumb'] as string,
          route: url || null,
        });
      }
      route = route.firstChild;
    }

    this.segments.set(segments);
  }
}
