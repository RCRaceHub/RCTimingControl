package dev.monkeypatch.rctiming.timing.dto;

/**
 * A transponder that belongs to a driver who is not in the race it was seen in. Sent in the list broadcast on
 * {@code /topic/race/{raceId}/wrong-heat} and returned by
 * {@code GET /api/v1/race-control/race/{raceId}/wrong-heat}. Its passings are not counted until an official approves.
 *
 * @param transponderNumber the number the decoder saw
 * @param entryId           the entry the number belongs to, in the event but not in this race
 * @param driverName        the public name live timing shows for that entry
 * @param carNumber         the car number the driver has in their own heat, null if none is recorded
 * @param heat              where the driver should be, such as {@code Heat 2} or {@code B final}; null if unknown
 * @param passings          how many passings the number has made in this race so far
 * @param status            what the race director or referee has decided
 */
public record WrongHeatDto(String transponderNumber, long entryId, String driverName, Integer carNumber, String heat,
                           int passings, Status status) {

    /** The decision on a flag. {@code OPEN} until an official acts; an approved flag has put the driver in the race. */
    public enum Status { OPEN, APPROVED, DISAPPROVED }

    /** The same flag with another status. */
    public WrongHeatDto withStatus(Status newStatus) {
        return new WrongHeatDto(transponderNumber, entryId, driverName, carNumber, heat, passings, newStatus);
    }

    /** The same flag with the current passing count. */
    public WrongHeatDto withPassings(int count) {
        return new WrongHeatDto(transponderNumber, entryId, driverName, carNumber, heat, count, status);
    }
}
