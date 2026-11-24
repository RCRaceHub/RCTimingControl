import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import DisplaysPage from './DisplaysPage';

vi.mock('@/pages/admin/board-views/BoardViewsPage', () => ({ default: () => <p>views page</p> }));
vi.mock('@/pages/admin/screens/ScreensPage', () => ({ default: () => <p>screens page</p> }));
vi.mock('@/pages/admin/board-views/OverlayDesignerPage', () => ({ default: () => <p>overlay page</p> }));

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <DisplaysPage />
    </MemoryRouter>,
  );
}

describe('DisplaysPage', () => {
  it('opens on the custom views', () => {
    renderAt('/admin/displays');
    expect(screen.getByText('views page')).toBeInTheDocument();
  });

  it.each([
    ['screens', 'screens page'],
    ['overlay', 'overlay page'],
  ])('opens the %s tab from the address', (tab, text) => {
    renderAt(`/admin/displays?tab=${tab}`);
    expect(screen.getByText(text)).toBeInTheDocument();
  });
});
