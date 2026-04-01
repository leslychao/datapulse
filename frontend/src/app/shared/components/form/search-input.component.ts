import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

@Component({
  selector: 'dp-search-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative h-8">
      <svg
        class="absolute left-2 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--text-tertiary)]"
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.3-4.3" />
      </svg>

      <input
        type="text"
        [placeholder]="placeholder()"
        [value]="value()"
        (input)="onInput($event)"
        class="h-full w-full rounded-[var(--radius-md)] border border-[var(--border-default)] bg-[var(--bg-primary)] pl-8 pr-8 text-[length:var(--text-base)] text-[var(--text-primary)] outline-none transition-colors focus:border-[var(--accent-primary)]"
      />

      @if (value()) {
        <button
          type="button"
          (click)="clear()"
          class="absolute right-2 top-1/2 flex -translate-y-1/2 cursor-pointer items-center justify-center text-[var(--text-tertiary)] transition-colors hover:text-[var(--text-primary)]"
          aria-label="Очистить поиск"
        >
          <svg
            class="h-4 w-4"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M18 6 6 18" />
            <path d="m6 6 12 12" />
          </svg>
        </button>
      }
    </div>
  `,
})
export class SearchInputComponent implements OnInit {
  readonly placeholder = input('');
  readonly debounceMs = input(300);

  readonly searchChange = output<string>();

  protected readonly value = signal('');

  private readonly destroyRef = inject(DestroyRef);
  private readonly search$ = new Subject<string>();

  ngOnInit(): void {
    this.search$
      .pipe(
        debounceTime(this.debounceMs()),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((term) => this.searchChange.emit(term));
  }

  protected onInput(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.value.set(val);
    this.search$.next(val);
  }

  protected clear(): void {
    this.value.set('');
    this.search$.next('');
    this.searchChange.emit('');
  }
}
