import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BackupsHelp } from './BackupsHelp';
import { ResultsExportsHelp } from './ResultsExportsHelp';
import { DecoderHelp } from './DecoderHelp';
import { EventManagementHelp } from './EventManagementHelp';
import { TransponderHelp } from './TransponderHelp';
import { WalkInHelp } from './WalkInHelp';
import { RaceControlHelp } from './RaceControlHelp';
import { ChampionshipHelp } from './ChampionshipHelp';
import { PracticeHelp } from './PracticeHelp';
import { RefereeHelp } from './RefereeHelp';
import { OfficialsHelp } from './OfficialsHelp';
import { SetupWizardHelp } from './SetupWizardHelp';
import { EntryManagementHelp } from './EntryManagementHelp';
import { CompetitorsHelp } from './CompetitorsHelp';

describe('help panels', () => {
  it('explain the admin screens', () => {
    const { unmount } = render(<BackupsHelp />);
    expect(screen.getByText('Back up now:')).toBeTruthy();
    unmount();
    const second = render(<ResultsExportsHelp />);
    expect(screen.getByText('Send now:')).toBeTruthy();
    second.unmount();
    render(<DecoderHelp />);
    expect(screen.getByText('Test Connection:')).toBeTruthy();
  });

  it('cover the entry imports, swaps on re-import and corrections', () => {
    const first = render(<EventManagementHelp />);
    expect(screen.getByText('Import from a CSV file:')).toBeTruthy();
    expect(screen.getByText('Entry feed:')).toBeTruthy();
    first.unmount();
    const second = render(<TransponderHelp />);
    expect(screen.getByText('After a re-import:')).toBeTruthy();
    second.unmount();
    render(<RaceControlHelp />);
    expect(screen.getByText('Corrections after the finish:')).toBeTruthy();
    expect(screen.getByText('Boards for screens:')).toBeTruthy();
  });

  it('match the Championship, Practice and Referee screens', () => {
    const first = render(<ChampionshipHelp />);
    expect(screen.getByText('Config:')).toBeTruthy();
    expect(screen.getByText('Link Event:')).toBeTruthy();
    first.unmount();
    const second = render(<PracticeHelp />);
    expect(screen.getByText('Print Results:')).toBeTruthy();
    expect(screen.getByText('Names on the table:')).toBeTruthy();
    second.unmount();
    render(<RefereeHelp />);
    expect(screen.getByText('Unknown transponders:')).toBeTruthy();
  });

  it('never mention racer accounts, portals or self-service', () => {
    for (const Panel of [ChampionshipHelp, PracticeHelp, RefereeHelp, EventManagementHelp, TransponderHelp, RaceControlHelp]) {
      const { container, unmount } = render(<Panel />);
      expect(container.textContent).not.toMatch(/portal|self-service|racer account|log ?in as a racer/i);
      unmount();
    }
  });

  it('link to the printable guides that cover the screen', () => {
    const adminOnly = [BackupsHelp, ResultsExportsHelp, DecoderHelp, OfficialsHelp, SetupWizardHelp];
    const meetingOnly = [TransponderHelp, WalkInHelp, RaceControlHelp, PracticeHelp, RefereeHelp];
    const both = [EntryManagementHelp, EventManagementHelp, ChampionshipHelp];
    const hrefs = (Panel: () => React.ReactElement) => {
      const { container, unmount } = render(<Panel />);
      const found = [...container.querySelectorAll('a')].map(a => a.getAttribute('href')!);
      unmount();
      // Every panel also links to its page of the docs site first
      expect(found[0]).toMatch(/^\/docs\//);
      return found.slice(1);
    };
    for (const Panel of adminOnly) expect(hrefs(Panel)).toEqual(['/print/admin-guide']);
    for (const Panel of meetingOnly) expect(hrefs(Panel)).toEqual(['/print/meeting-guide']);
    for (const Panel of both) expect(hrefs(Panel)).toEqual(['/print/admin-guide', '/print/meeting-guide']);
  });

  it('explain how to fix a mispronounced name', () => {
    render(<CompetitorsHelp />);
    expect(screen.getByText('Say as:')).toBeTruthy();
    expect(screen.getByText('When it takes effect:')).toBeTruthy();
  });
});
