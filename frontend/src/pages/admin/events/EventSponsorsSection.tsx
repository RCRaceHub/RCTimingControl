import { SponsorListEditor } from '@/components/SponsorListEditor';
import { useEventSponsors, useSponsorMutations } from '@/hooks/admin/useEventSponsors';

interface EventSponsorsSectionProps {
  eventId: number;
  /** Whether the signed-in official may change the list; others see it read only. */
  canEdit: boolean;
}

/**
 * An event's sponsors (#518), in the order the overlay's sponsor bar shows them. Entered here in RCTC; a RaceHub
 * import may fill the list later. An overlay theme that has sponsors of its own shows those instead.
 */
export default function EventSponsorsSection({ eventId, canEdit }: EventSponsorsSectionProps) {
  const { data: sponsors = [], isLoading } = useEventSponsors(eventId);
  return <SponsorListEditor sponsors={sponsors} isLoading={isLoading} mutations={useSponsorMutations(eventId)} canEdit={canEdit} />;
}
