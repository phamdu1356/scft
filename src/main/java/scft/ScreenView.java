package scft;

import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import org.kordamp.ikonli.javafx.FontIcon;

/** Mirror stage. Shows a phone frame (phone→PC) or a landscape desktop frame (PC→phone). */
public class ScreenView extends VBox {
    private final LinkService link;
    private final Canvas canvas = new Canvas(360, 640);
    private final StackPane stage = new StackPane(canvas);
    private final AnimationTimer timer;
    private final Label empty = new Label();
    private long t0 = System.nanoTime();
    private String lastA11y = "";

    public ScreenView(LinkService link) {
        super(16);
        this.link = link;
        setPadding(new Insets(28));

        Label h = new Label("Màn hình");
        h.getStyleClass().add("title-2");

        // Always-visible notice: this screen is a simulation, not a real mirror.
        Label badge = new Label("MÔ PHỎNG", new FontIcon("fth-alert-triangle"));
        badge.getStyleClass().add("sim-badge");
        Label banner = new Label("Tính năng đang phát triển: khung dưới đây chỉ là hình minh họa, "
                + "chưa có hình màn hình thật từ thiết bị.");
        banner.setWrapText(true);
        banner.setMaxWidth(Double.MAX_VALUE);
        HBox notice = new HBox(12, badge, banner);
        notice.setAlignment(Pos.CENTER_LEFT);
        notice.setPadding(new Insets(10, 14, 10, 14));
        notice.getStyleClass().add("sim-banner");

        ToggleGroup g = new ToggleGroup();
        ToggleButton a = dirButton(Direction.PHONE_TO_PC, "fth-arrow-right", g);
        ToggleButton b = dirButton(Direction.PC_TO_PHONE, "fth-arrow-left", g);
        a.setSelected(true);
        g.selectedToggleProperty().addListener((o, old, n) -> {
            if (n == null) { old.setSelected(true); return; }
            link.directionProperty().set((Direction) n.getUserData());
            link.stopMirroring(); // the simulated mirror does not survive a direction change
            resize();
        });
        HBox seg = new HBox(a, b);
        seg.getStyleClass().add("segmented");

        Button go = new Button();
        go.textProperty().bind(Bindings.when(link.mirroringProperty())
                .then("Dừng chiếu thử").otherwise("Chiếu thử (mô phỏng)"));
        go.graphicProperty().bind(Bindings.when(link.mirroringProperty())
                .then((javafx.scene.Node) new FontIcon("fth-square")).otherwise(new FontIcon("fth-play")));
        go.getStyleClass().add("accent");
        link.mirroringProperty().addListener((o, was, on) -> {
            go.getStyleClass().removeAll("accent", "danger");
            go.getStyleClass().add(on ? "danger" : "accent"); // stop is red, start is accent
        });
        go.disableProperty().bind(link.connectedProperty().not());
        go.setOnAction(e -> link.toggleMirroring());

        Label needDevice = new Label("Kết nối một thiết bị trước để chiếu thử.");
        needDevice.getStyleClass().add("text-muted");
        needDevice.visibleProperty().bind(link.connectedProperty().not());
        needDevice.managedProperty().bind(needDevice.visibleProperty());

        // FlowPane wraps the controls onto a second row instead of overflowing at 900px.
        FlowPane bar = new FlowPane(12, 12, seg, go, needDevice);
        bar.setAlignment(Pos.CENTER_LEFT);

        empty.setWrapText(true);
        empty.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        empty.getStyleClass().add("stage-empty");
        empty.textProperty().bind(Bindings.when(link.connectedProperty())
                .then("Nhấn \"Chiếu thử (mô phỏng)\" để xem khung minh họa.")
                .otherwise("Chưa có thiết bị. Vào mục Thiết bị để ghép nối."));
        empty.maxWidthProperty().bind(canvas.widthProperty().subtract(48));
        empty.visibleProperty().bind(link.connectedProperty().and(link.mirroringProperty()).not());
        empty.managedProperty().bind(empty.visibleProperty());

        stage.getStyleClass().add("stage");
        stage.setMinHeight(420);
        stage.setPrefHeight(460);
        stage.getChildren().add(empty);
        stage.layoutBoundsProperty().addListener((o, x, y) -> resize());

        getChildren().addAll(h, notice, bar, stage);

        timer = new AnimationTimer() {
            @Override public void handle(long now) { draw((now - t0) / 1e9); }
        };
    }

    /** Redraw only while this screen is visible; Main calls this when the tab changes. */
    public void setActive(boolean on) {
        if (on) timer.start();
        else timer.stop();
    }

    private ToggleButton dirButton(Direction d, String icon, ToggleGroup g) {
        ToggleButton t = new ToggleButton(d.shortLabel(), new FontIcon(icon));
        t.setTooltip(new Tooltip(d.toString()));
        t.setUserData(d);
        t.setToggleGroup(g);
        return t;
    }

    /** Fit the frame into the stage while keeping the phone (9:16) or desktop (16:10) aspect. */
    private void resize() {
        double w = Math.max(160, stage.getWidth() - 40), h = Math.max(300, stage.getHeight() - 40);
        double ratio = link.directionProperty().get() == Direction.PHONE_TO_PC ? 9.0 / 16 : 16.0 / 10;
        double cw = Math.min(w, h * ratio);
        canvas.setWidth(cw);
        canvas.setHeight(cw / ratio);
    }

    private void draw(double t) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        boolean live = link.connectedProperty().get() && link.mirroringProperty().get();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#0B1220"));
        g.fillRoundRect(0, 0, w, h, 28, 28);
        g.save();
        g.beginPath();
        g.rect(8, 8, w - 16, h - 16);
        g.clip();
        if (live) {
            g.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#1E3A8A")), new Stop(1, Color.web("#2563EB"))));
            g.fillRect(8, 8, w - 16, h - 16);
            g.setFill(Color.color(1, 1, 1, 0.9));
            for (int i = 0; i < 4; i++) {
                double y = 60 + i * (h / 5) + Math.sin(t * 1.2 + i) * 6;
                g.setFill(Color.color(1, 1, 1, 0.14));
                g.fillRoundRect(24, y, w - 48, h / 6.5, 14, 14);
            }
        } else {
            g.setFill(Color.web("#111B2E"));
            g.fillRect(8, 8, w - 16, h - 16);
        }
        g.restore();
        if (live) {
            g.setFill(Color.web("#0B1220", 0.7));
            g.fillRoundRect(16, h - 40, w - 32, 26, 8, 8);
            g.setFill(Color.web("#F8FAFC"));
            g.setFont(javafx.scene.text.Font.font(13));
            g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
            g.fillText("MÔ PHỎNG · chưa có nguồn hình thật", 24, h - 22);
        }
        String a11y = live ? "Đang chiếu thử (mô phỏng), chưa có hình thật"
                : link.connectedProperty().get() ? "Chưa chiếu" : "Chưa kết nối thiết bị";
        if (!a11y.equals(lastA11y)) { // only publish changes, not every frame
            lastA11y = a11y;
            canvas.setAccessibleText(a11y);
        }
    }
}
