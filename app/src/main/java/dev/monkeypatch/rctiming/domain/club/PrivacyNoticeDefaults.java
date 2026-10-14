package dev.monkeypatch.rctiming.domain.club;

/**
 * The privacy notice shown until a club writes its own (#267). It describes what RCTC itself holds; the
 * club is free to replace it, so it is a starting point and not legal advice.
 */
final class PrivacyNoticeDefaults {

    private PrivacyNoticeDefaults() {}

    /** The default notice, with the club's name and how to reach it filled in. */
    static String text(String clubName, String email, String phone) {
        String club = clubName == null || clubName.isBlank() ? "the club" : clubName.trim();
        StringBuilder contact = new StringBuilder();
        if (email != null && !email.isBlank()) contact.append(email.trim());
        if (phone != null && !phone.isBlank()) {
            if (contact.length() > 0) contact.append(" or ");
            contact.append(phone.trim());
        }
        String reach = contact.length() > 0 ? "Contact " + club + " on " + contact
                : "Ask a club official";
        return """
                How %s uses your information at race meetings

                %s runs race timing and race control with RC Timing Control. This notice says what that program holds about you.

                What we hold
                - Your name, and where the club has them, your BRCA number, your home club and how your name is pronounced for the announcer.
                - The transponder numbers you race with, the classes you entered and your car number.
                - Your lap times, positions, results and championship points, and any penalties or incident reports about you.
                - Practice laps, if you practise at the track.

                Why we hold it
                We need it to time and score the meeting, run the announcer, publish results and keep the club's championship standings.

                Who can see it
                - Results, live timing, the boards and championship standings are shown to anyone on the venue network, and may be shown to remote viewers on the club's live feed. They show your name, or only your initials, or no name at all if the club has set that for you.
                - Only club officials can sign in to RC Timing Control and see the full records.
                - Your booking, contact and payment details are held by RaceHub, not by the timing program.

                How long we keep it
                Results and championship history are kept as the club's record. Sign-in records are cleared on a schedule. Ask us if you want something removed sooner.

                Your rights
                You can ask for a copy of your data, to have it corrected, or to have your name removed from the club's records. %s.
                """.formatted(club, Character.toUpperCase(club.charAt(0)) + club.substring(1), reach);
    }
}
