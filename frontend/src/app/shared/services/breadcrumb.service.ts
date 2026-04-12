import { Injectable, inject, signal } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { filter } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';

export interface BreadcrumbSegment {
  label: string;
  route: string | null;
}

@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);

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
      const pathSegments = route.snapshot.url.map((seg) => seg.path);
      if (pathSegments.length > 0) {
        url += '/' + pathSegments.join('/');
      }
      const key = route.snapshot.data['breadcrumb'] as string | undefined;
      if (key && pathSegments.length > 0) {
        const translated = this.translate.instant(key);
        segments.push({
          label: translated !== key ? translated : key,
          route: url,
        });
      }
      route = route.firstChild;
    }

    this.segments.set(segments);
  }
}
