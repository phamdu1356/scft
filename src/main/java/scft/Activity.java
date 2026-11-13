package scft;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One line of the activity history. arrow is "←" received, "→" sent, "•" local/system. */
public record Activity(LocalDateTime at, String kind, String arrow, String text) {
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    public String line() {
        return at.format(HMS) + "  " + arrow + "  " + kind + ": " + text;
    }

    /** Shortens long text (clipboard, chat) for one-line display. */
    static String preview(String s) {
        String one = s.replace('\n', ' ').replace('\r', ' ').trim();
        return one.length() > 80 ? one.substring(0, 80) + "…" : one;
    }
}
