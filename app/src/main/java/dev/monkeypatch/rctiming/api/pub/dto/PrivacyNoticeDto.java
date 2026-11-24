package dev.monkeypatch.rctiming.api.pub.dto;


/** The privacy notice page's data: the club's name, contact {@code email} and {@code phone}, and the notice text. */
public record PrivacyNoticeDto(String clubName, String email, String phone, String text) {}
