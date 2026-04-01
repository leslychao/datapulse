import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
type ButtonSize = 'sm' | 'md' | 'lg';

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-[var(--accent-primary)] text-white hover:bg-[var(--accent-primary-hover)]',
  secondary: 'border border-[var(--border-default)] bg-[var(--bg-primary)] text-[var(--text-primary)] hover:bg-[var(--bg-tertiary)]',
  ghost: 'text-[var(--text-secondary)] hover:bg-[var(--bg-tertiary)] hover:text-[var(--text-primary)]',
  danger: 'bg-[var(--status-error)] text-white hover:bg-[color-mix(in_srgb,var(--status-error)_85%,black)]',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'h-7 px-3 text-xs gap-1',
  md: 'h-8 px-4 text-sm gap-1.5',
  lg: 'h-10 px-5 text-sm gap-2',
};

@Component({
  selector: 'dp-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type()"
      [disabled]="disabled() || loading()"
      (click)="clicked.emit($event)"
      class="inline-flex cursor-pointer items-center justify-center rounded-[var(--radius-md)] font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50"
      [class]="variantClass() + ' ' + sizeClass()"
    >
      @if (loading()) {
        <span
          class="dp-spinner inline-block rounded-full border-2 border-current/30"
          style="border-top-color: currentColor"
          [style.width.px]="size() === 'sm' ? 12 : 14"
          [style.height.px]="size() === 'sm' ? 12 : 14"
        ></span>
      }
      <ng-content />
    </button>
  `,
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);

  readonly clicked = output<MouseEvent>();

  protected readonly variantClass = () => VARIANT_CLASSES[this.variant()];
  protected readonly sizeClass = () => SIZE_CLASSES[this.size()];
}
