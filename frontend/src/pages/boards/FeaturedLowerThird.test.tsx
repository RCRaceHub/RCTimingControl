import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { LiveTimingRowDto } from '@/lib/raceControlApi';
import type { RaceMediaDto } from '@/lib/boardsApi';
import { FeaturedLowerThird } from './FeaturedLowerThird';

const row = { entryId: 4, position: 2, carNumber: '7', driverName: 'Pat Racer' } as LiveTimingRowDto;
const props = { panelClass: '', mutedClass: '', bottom: '0px' };

describe('FeaturedLowerThird', () => {
  it('shows the name, number, place, sponsors and headshot', () => {
    const media = new Map<number, RaceMediaDto>([
      [4, { entryId: 4, headshotUrl: '/pat.jpg', carPhotoUrl: null, sponsors: ['Acme', 'Tyres R Us'] }],
    ]);
    const { container } = render(<FeaturedLowerThird entryId={4} rows={[row]} media={media} {...props} />);

    expect(screen.getByText('Pat Racer')).toBeInTheDocument();
    expect(screen.getByText('P2')).toBeInTheDocument();
    expect(screen.getByText('#7')).toBeInTheDocument();
    expect(screen.getByText('Acme · Tyres R Us')).toBeInTheDocument();
    expect(container.querySelector('img')).toHaveAttribute('src', '/pat.jpg');
  });

  it('shows a plain card when no photo is held', () => {
    const { container } = render(<FeaturedLowerThird entryId={4} rows={[row]} media={new Map()} {...props} />);

    expect(screen.getByText('Pat Racer')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });

  it('shows nothing with no car or an unknown one', () => {
    const { container, rerender } = render(<FeaturedLowerThird entryId={null} rows={[row]} media={new Map()} {...props} />);
    expect(container).toBeEmptyDOMElement();
    rerender(<FeaturedLowerThird entryId={99} rows={[row]} media={new Map()} {...props} />);
    expect(container).toBeEmptyDOMElement();
  });
});
