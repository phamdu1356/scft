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
    private long t0 = System.nanoTime();

    public ScreenView(LinkService link) {
        super(16);
        this.link = link;
        setPadding(new Insets(28));

        Label h = new Label("Màn hình");
        h.getStyleClass().add("title-2");

        ToggleGroup g = new ToggleGroup();
        ToggleButton a = dirButton(Direction.PHONE_TO_PC, "fth-arrow-right", g);
        ToggleButton b = dirButton(Direction.PC_TO_PHONE, "fth-arrow-left", g);
        a.setSelected(true);
        g.selectedToggleProperty().addListener((o, old, n) -> {
            if (n == null) { old.setSelected(true); return; }
            link.directionProperty().set((Direction) n.getUserData());
            resize();
        });
        HBox seg = new HBox(a, b);
        seg.getStyleClass().add("segmented");

        ChoiceBox<String> quality = new ChoiceBox<>();
        quality.getItems().addAll("Tự động", "720p · 30 fps", "1080p · 60 fps");
        quality.setValue("Tự động");

        Button go = new Button();
        go.textProperty().bind(Bindings.when(link.mirroringProperty()).then("Dừng chiếu").otherwise("Bắt đầu chiếu"));
        go.graphicProperty().bind(Bindings.when(link.mirroringProperty())
                .then((javafx.scene.Node) new FontIcon("fth-square")).otherwise(new FontIcon("fth-play")));
        go.getStyleClass().add("accent");
        go.disableProperty().bind(link.connectedProperty().not());
        go.setOnAction(e -> link.toggleMirroring());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox bar = new HBox(12, seg, quality, sp, go);
        bar.setAlignment(Pos.CENTER_LEFT);

        stage.getStyleClass().add("stage");
        VBox.setVgrow(stage, Priority.ALWAYS);
        stage.layoutBoundsProperty().addListener((o, x, y) -> resize());

        getChildren().addAll(h, bar, stage);

        new AnimationTimer() {
            @Override public void handle(long now) { draw((now - t0) / 1e9); }
        }.start();
    }

    private ToggleButton dirButton(Direction d, String icon, ToggleGroup g) {
        ToggleButton t = new ToggleButton(d.toString(), new FontIcon(icon));
        t.setUserData(d);
        t.setToggleGroup(g);
        return t;
    }

    /** Fit the frame into the stage while keeping the phone (9:16) or desktop (16:10) aspect. */
    private void resize() {
        double w = Math.max(100, stage.getWidth() - 40), h = Math.max(100, stage.getHeight() - 40);
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
            g.setFill(Color.web("#94A3B8"));
            g.setFont(javafx.scene.text.Font.font(14));
            g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            String msg = link.connectedProperty().get()
                    ? "Nhấn \"Bắt đầu chiếu\"" : "Chưa có thiết bị. Vào mục Thiết bị để ghép nối";
            g.fillText(msg, w / 2, h / 2);
        }
        g.restore();
        if (live) {
            g.setFill(Color.web("#F8FAFC"));
            g.setFont(javafx.scene.text.Font.font(11));
            g.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
            g.fillText("MÔ PHỎNG · chưa có nguồn hình thật", 18, h - 16);
        }
    }
}
