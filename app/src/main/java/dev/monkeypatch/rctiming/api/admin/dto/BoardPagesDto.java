package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.BoardPageCatalog;

import java.util.List;

/**
 * The pages of this app to offer when building a playlist (#708): each page's path joins to any of the base
 * addresses, which are the ones other computers on the venue network can reach (never localhost).
 */
public record BoardPagesDto(List<String> baseUrls, List<BoardPageCatalog.Page> pages) {

    public static BoardPagesDto from(BoardPageCatalog.Choices choices) {
        return new BoardPagesDto(choices.baseUrls(), choices.pages());
    }
}
