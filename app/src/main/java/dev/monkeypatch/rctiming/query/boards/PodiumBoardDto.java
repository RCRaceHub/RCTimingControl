package dev.monkeypatch.rctiming.query.boards;

import java.util.List;

/**
 * The podium of each class and the leader of each category (Junior, Vet and so on) in it, for anyone on the venue
 * network. Names are as the public may see them (#265). Answer of {@code GET /api/v1/boards/podium}; {@code eventId}
 * and {@code eventName} are null, and {@code classes} empty, when no event is found.
 */
public record PodiumBoardDto(Long eventId, String eventName, List<ClassPodium> classes) {

    /**
     * One class.
     *
     * @param eventClassId    the class in this event
     * @param className       the racing class's name
     * @param complete        whether every final of the class has finished; until then the category leaders are only
     *                        the best so far
     * @param aFinalAbandoned whether the A final was abandoned, so there is no podium and none is coming
     * @param podium          the first three of the A final, best first; empty until the A final has finished
     * @param categoryLeaders the best-placed finisher (not a DNF) of each category in the class's finals, in name
     *                        order; looks only at finals up to the first one still to run
     */
    public record ClassPodium(Long eventClassId, String className, boolean complete, boolean aFinalAbandoned, List<Place> podium,
                              List<CategoryLeader> categoryLeaders) {}

    /**
     * A podium place.
     *
     * @param position  1, 2 or 3
     * @param driverName the name as the public may see it
     * @param carNumber the car number, or null
     */
    public record Place(int position, String driverName, String carNumber) {}

    /**
     * The best-placed driver holding a category.
     *
     * @param category        the club's own word for the category, as first spelt on a driver
     * @param driverName      the name as the public may see it
     * @param carNumber       the car number, or null
     * @param overallPosition the driver's place in the class's finals taken together (the A final, then the B final
     *                        and so on); 1 for the class winner
     */
    public record CategoryLeader(String category, String driverName, String carNumber, int overallPosition) {}
}
