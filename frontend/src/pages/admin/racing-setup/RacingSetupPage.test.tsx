import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import RacingSetupPage from './RacingSetupPage';

vi.mock('@/pages/admin/classes/ClassesPage', () => ({ default: () => <p>classes page</p> }));
vi.mock('@/pages/admin/formats/FormatsPage', () => ({ default: () => <p>formats page</p> }));
vi.mock('@/pages/admin/tracks/TracksPage', () => ({ default: () => <p>tracks page</p> }));
vi.mock('@/pages/admin/scoring/ScoringSystemsPage', () => ({ default: () => <p>scoring page</p> }));

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <RacingSetupPage />
    </MemoryRouter>,
  );
}

describe('RacingSetupPage', () => {
  it('opens on classes', () => {
    renderAt('/admin/racing-setup');
    expect(screen.getByText('classes page')).toBeInTheDocument();
  });

  it.each([
    ['formats', 'formats page'],
    ['tracks', 'tracks page'],
    ['scoring', 'scoring page'],
  ])('opens the %s tab from the address', (tab, text) => {
    renderAt(`/admin/racing-setup?tab=${tab}`);
    expect(screen.getByText(text)).toBeInTheDocument();
  });
});
