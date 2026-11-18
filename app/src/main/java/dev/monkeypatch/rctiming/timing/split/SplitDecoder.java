package dev.monkeypatch.rctiming.timing.split;

/**
 * A decoder at a split point on the track (#672). It reads the RC-4 text protocol like the main decoder, but its
 * passings never credit a lap: they give sector times.
 *
 * @param id            the row id, null before it is saved
 * @param name          what officials call the loop, such as "Back straight"
 * @param host          the decoder's address on the venue network
 * @param port          the decoder's RC-4 text port, normally 5100
 * @param splitNumber   1 for the first split after the start/finish loop, 2 for the next, up to 9; unique
 * @param clockOffsetMs added to every passing time from this decoder, to line its server-anchored clock up with the
 *                      main decoder's; negative when this decoder's passings arrive late
 * @param enabled       false keeps the row but does not connect
 */
public record SplitDecoder(Long id, String name, String host, int port, int splitNumber, long clockOffsetMs,
                           boolean enabled) {
}
