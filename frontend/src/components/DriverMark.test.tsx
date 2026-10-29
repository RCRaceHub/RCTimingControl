import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DriverMark } from './DriverMark';

const media = { entryId: 1, headshotUrl: '/h.png', carPhotoUrl: null, sponsors: [], carColours: ['#ff0000', '#ffffff'] };

describe('DriverMark', () => {
  it('draws the headshot and the entry colours when both are asked for', () => {
    render(<DriverMark media={media} headshot colours />);
    expect(screen.getByTestId('driver-mark-headshot')).toHaveAttribute('src', '/h.png');
    expect(screen.getByTestId('driver-mark-colours')).toBeInTheDocument();
  });

  it('draws only what is asked for', () => {
    render(<DriverMark media={media} headshot={false} colours />);
    expect(screen.queryByTestId('driver-mark-headshot')).toBeNull();
    expect(screen.getByTestId('driver-mark-colours')).toBeInTheDocument();
  });

  it('falls back to the class colour and draws nothing with no picture and no colour', () => {
    const { rerender, container } = render(
      <DriverMark media={undefined} classColour="#00ff00" headshot colours />,
    );
    expect(screen.getByTestId('driver-mark-colours')).toBeInTheDocument();
    rerender(<DriverMark media={undefined} headshot colours />);
    expect(container).toBeEmptyDOMElement();
  });
});
