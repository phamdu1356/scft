package scft;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TransferItem {
    public final String name;
    public final long bytes;
    public final Direction direction;
    public final DoubleProperty progress = new SimpleDoubleProperty(0);
    public final StringProperty status = new SimpleStringProperty("Đang chờ");

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
}
