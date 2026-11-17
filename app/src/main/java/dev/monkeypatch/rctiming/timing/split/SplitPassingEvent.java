package dev.monkeypatch.rctiming.timing.split;

/**
 * Published for each transponder passing a split loop (#672). It is never a lap: {@code DecoderListener}'s
 * {@code LapPassingEvent} stays the only thing that credits laps, so a split loop cannot be counted as the finish.
 *
 * <p>Published by {@link SplitLoopListener} on the single timing thread ({@code timingExecutor}), the same thread
 * that publishes the finish loop's passings, so a listener sees both kinds in the order they reached the server.
 *
 * @param raceId           the running race, or {@code LapPassingEvent.NO_RACE} when none is running
 * @param splitDecoderId   the {@code split_decoders} row the passing came from
 * @param splitNumber      1 for the first split after the start/finish loop
 * @param transponderNumber the transponder id as the decoder sent it
 * @param rtcTimeMicros    UTC microseconds, server-anchored and already shifted by the decoder's clock offset
 */
public record SplitPassingEvent(long raceId, long splitDecoderId, int splitNumber, String transponderNumber,
                                long rtcTimeMicros) {
}
