package scft;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seam between UI and transport. This implementation SIMULATES a paired phone so the UI is
 * fully usable; replace the bodies of connect/startMirroring/send with a real transport
 * (e.g. socket + screen capture) without touching the views.
 */
public class LinkService {
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty deviceName = new SimpleStringProperty("—");
    private final StringProperty statusText = new SimpleStringProperty("Chưa kết nối thiết bị");
    private final BooleanProperty mirroring = new SimpleBooleanProperty(false);
    private final ObjectProperty<Direction> direction = new SimpleObjectProperty<>(Direction.PHONE_TO_PC);
    private final ObservableList<TransferItem> transfers = FXCollections.observableArrayList();
    private final StringProperty pairingCode = new SimpleStringProperty(newCode());

    public BooleanProperty connectedProperty() { return connected; }
    public StringProperty deviceNameProperty() { return deviceName; }
    public StringProperty statusTextProperty() { return statusText; }
    public BooleanProperty mirroringProperty() { return mirroring; }
    public ObjectProperty<Direction> directionProperty() { return direction; }
    public ObservableList<TransferItem> transfers() { return transfers; }
    public StringProperty pairingCodeProperty() { return pairingCode; }

    public void connect() {
        connected.set(true);
        deviceName.set("Điện thoại (mô phỏng)");
        statusText.set("Đã kết nối: " + deviceName.get());
    }

    public void disconnect() {
        mirroring.set(false);
        connected.set(false);
        deviceName.set("—");
        statusText.set("Chưa kết nối thiết bị");
        pairingCode.set(newCode());
    }

    public void toggleMirroring() {
        if (!connected.get()) return;
        mirroring.set(!mirroring.get());
        statusText.set(mirroring.get()
                ? "Đang chiếu: " + direction.get()
                : "Đã kết nối: " + deviceName.get());
    }

    public void send(File f) {
        if (!connected.get()) return;
        TransferItem t = new TransferItem(f.getName(), f.length(), direction.get());
        transfers.add(0, t);
        t.status.set("Đang truyền");
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(80), e -> {
            double p = Math.min(1, t.progress.get() + ThreadLocalRandom.current().nextDouble(0.02, 0.08));
            t.progress.set(p);
            if (p >= 1) t.status.set("Hoàn tất");
        }));
        tl.setCycleCount(Timeline.INDEFINITE);
        t.progress.addListener((o, a, p) -> { if (p.doubleValue() >= 1) tl.stop(); });
        tl.play();
    }

    private static String newCode() {
        int n = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return (n / 1000) + " " + (n % 1000);
    }
}
