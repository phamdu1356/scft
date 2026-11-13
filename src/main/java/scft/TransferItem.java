package scft;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;

public class TransferItem {
    public final String name;
    public final long bytes;
    public final Direction direction;
    public final DoubleProperty progress = new SimpleDoubleProperty(0);
    public final StringProperty status = new SimpleStringProperty("Đang chờ");
    /** True while queued, receiving or sending; false once done, failed or cancelled. */
    public final BooleanProperty active = new SimpleBooleanProperty(false);
    /** Set when the link was closed while this item was active. Read from worker threads. */
    public volatile boolean cancelled;
    /** Where a received file was saved (null for sent files or unfinished receives). */
    public final ObjectProperty<Path> savedTo = new SimpleObjectProperty<>();
    /** Bytes moved so far. */
    public final LongProperty done = new SimpleLongProperty(0);
    /** Smoothed speed in bytes per second (0 when unknown or finished). */
    public final DoubleProperty speed = new SimpleDoubleProperty(0);
    /** Hex SHA-256 of the whole file, empty until the transfer completes. */
    public final StringProperty sha256 = new SimpleStringProperty("");

    public TransferItem(String name, long bytes, Direction direction) {
        this.name = name;
        this.bytes = bytes;
        this.direction = direction;
    }

    public static String human(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format("%.1f %s", v, u[i]);
    }

    public static String rate(double bytesPerSecond) {
        return human((long) bytesPerSecond) + "/s";
    }

    /** Remaining time as Vietnamese text, e.g. "45 s" or "2 phút 5 s". */
    public static String eta(long remainingBytes, double bytesPerSecond) {
        if (bytesPerSecond <= 0) return "—";
        long s = (long) Math.ceil(Math.max(0, remainingBytes) / bytesPerSecond);
        if (s < 60) return s + " s";
        if (s < 3600) return (s / 60) + " phút " + (s % 60) + " s";
        return (s / 3600) + " giờ " + (s % 3600 / 60) + " phút";
    }
}
