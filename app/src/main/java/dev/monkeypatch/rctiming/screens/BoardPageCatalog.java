package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.domain.boardview.BoardViewService;
import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The pages of this app worth putting on a screen (#708), so an official picks one instead of typing a URL: the
 * spectator boards and every saved board view. The screens are other computers, so each page is offered under the
 * server's venue-network addresses from {@link LanAddresses} (an IP address, the host name, the mDNS name), never
 * {@code localhost}, which on a Pi would mean the Pi itself.
 */
@Component
public class BoardPageCatalog {

    /** A page: what to call it and its path from the site root, e.g. {@code /boards/results}. */
    public record Page(String label, String path) { }

    /** The addresses the screens can reach this app at (each ending in {@code /}, possibly none yet) and the pages. */
    public record Choices(List<String> baseUrls, List<Page> pages) { }

    private final LanAddresses lan;
    private final BoardViewService boardViews;

    public BoardPageCatalog(LanAddresses lan, BoardViewService boardViews) {
        this.lan = lan;
        this.boardViews = boardViews;
    }

    /** The addresses and pages as they are now. */
    public Choices choices() {
        List<Page> pages = new ArrayList<>(List.of(
                new Page("Now and next", "/boards/now-next"),
                new Page("Results", "/boards/results"),
                new Page("Live race", "/boards/live-race"),
                new Page("Live top", "/boards/top")));
        boardViews.list().forEach(view -> pages.add(new Page("View: " + view.getName(), "/boards/view/" + view.getSlug())));
        return new Choices(lan.urls(), pages);
    }
}
