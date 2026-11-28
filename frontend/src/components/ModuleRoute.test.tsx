import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import { ModuleRoute } from './ModuleRoute';

const state = vi.hoisted(() => ({ disabled: new Set<string>() }));
vi.mock('@/hooks/admin/useModules', () => ({ useDisabledModules: () => state.disabled }));

function renderRoute() {
  render(
    <MemoryRouter>
      <ModuleRoute module="screens">
        <p>screens page</p>
      </ModuleRoute>
    </MemoryRouter>,
  );
}

describe('ModuleRoute', () => {
  it('shows the page while the module is on', () => {
    state.disabled = new Set();
    renderRoute();
    expect(screen.getByText('screens page')).toBeInTheDocument();
  });

  it('shows not found while the module is off', () => {
    state.disabled = new Set(['screens']);
    renderRoute();
    expect(screen.queryByText('screens page')).not.toBeInTheDocument();
  });
});
