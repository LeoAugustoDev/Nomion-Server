import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-icon',
  template: `<svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.5"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path [attr.d]="paths[name()] ?? paths['file']" />
  </svg>`,
  styles: [
    ':host { display: inline-flex; flex-shrink: 0; width: 1.15rem; height: 1.15rem; } svg { width: 100%; height: 100%; }',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icon {
  readonly name = input('file');
  protected readonly paths: Record<string, string> = {
    grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
    building:
      'M3 21V7h6 M9 21V3h9v18 M18 10h3v11 M12 7h3 M12 11h3 M12 15h3 M6 11v1 M6 15v1 M12 21v-3h3v3',
    file: 'M6 3h8l4 4v14H6z M14 3v5h4 M9 12h6 M9 16h6',
    calendar: 'M3 5h18v16H3z M7 3v4 M17 3v4 M3 10h18 M7 14h2 M13 14h2 M7 17h2',
    scale: 'M12 3v18 M6 21h12 M3 7h18 M6 7l-4 8h8z M18 7l-4 8h8z',
    users:
      'M16 21v-3a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v3 M13 4a4 4 0 0 1 0 8 M22 21v-3a4 4 0 0 0-4-4 M5 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0',
    user: 'M12 22a10 10 0 1 0 0-20a10 10 0 1 0 0 20 M8 9a4 4 0 1 0 8 0a4 4 0 1 0-8 0 M5 19a7 7 0 0 1 14 0',
    folder: 'M3 7V4h6l3 3h9v13H3z',
    spark: 'M12 3l2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z',
    exit: 'M9 3H4v18h5 M13 7l5 5-5 5 M8 12h13',
    chevron: 'M9 5l7 7-7 7',
    switch: 'M8 8l4-4 4 4 M8 16l4 4 4-4',
    search: 'M10 18a8 8 0 1 0 0-16a8 8 0 1 0 0 16 M16 16l6 6',
    plus: 'M12 5v14 M5 12h14',
    close: 'M6 6l12 12 M6 18L18 6',
    menu: 'M3 6h18 M3 12h18 M3 18h18',
    shield: 'M12 2l8 4v6c0 5-8 10-8 10S4 17 4 12V6z M8 12l3 3 5-6',
    alert: 'M12 3L2 21h20z M12 9v5 M12 17v.1',
  };
}
