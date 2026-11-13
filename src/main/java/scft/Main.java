package scft;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Toolkit;
import java.util.prefs.Preferences;

/** Application shell: sidebar navigation + error banner + swappable content + status bar. */
public class Main extends Application {

    private final LinkService link = new LinkService();
    private final BorderPane root = new BorderPane();
    private final BooleanProperty dark = new SimpleBooleanProperty(false);
    private final ObjectProperty<Node> current = new SimpleObjectProperty<>();
    private Node devicesNode;
    private Node screenNode;
    private ScreenView screenView;
    private Stage stage;
    private Timeline ringing;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        dark.set(prefs().getBoolean("dark", false));
        applyTheme(dark.get());
        dark.addListener((o, a, isDark) -> {
            applyTheme(isDark);
            try { prefs().putBoolean("dark", isDark); } catch (RuntimeException ignored) { }
        });

        DevicesView devicesView = new DevicesView(link);
        screenView = new ScreenView(link);
        devicesNode = wrap(devicesView);
        screenNode = wrap(screenView);

        ToggleGroup nav = new ToggleGroup();
        ToggleButton devNav = navButton("Thiết bị", "fth-smartphone", "Ctrl+1", nav, devicesNode);
        Node files = wrap(new FilesView(link, devNav::fire));
        ToggleButton screenNav = navButton("Màn hình", "fth-monitor", "Ctrl+2", nav, screenNode);
        ToggleButton filesNav = navButton("Tệp & dữ liệu", "fth-repeat", "Ctrl+3", nav, files);
        Node utilsNode = wrap(new UtilsView(link));
        ToggleButton utilsNav = navButton("Tiện ích", "fth-tool", "Ctrl+4", nav, utilsNode);

        // Find-device from the other side: beep and shake the window for about 10 seconds.
        link.addListener(m -> {
            if (m instanceof Msg.Find f) ring(f.on());
        });

        VBox sidebar = new VBox(4, brand(), devNav, screenNav, filesNav, utilsNav);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.setPrefWidth(220);

        ToggleButton theme = new ToggleButton();
        theme.textProperty().bind(Bindings.when(dark)
                .then("Chuyển sang giao diện sáng").otherwise("Chuyển sang giao diện tối"));
        theme.graphicProperty().bind(Bindings.when(dark)
                .then((Node) new FontIcon("fth-sun")).otherwise(new FontIcon("fth-moon")));
        theme.setMaxWidth(Double.MAX_VALUE);
        theme.selectedProperty().bindBidirectional(dark);
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        sidebar.getChildren().addAll(grow, theme);

        root.setLeft(sidebar);
        root.setTop(errorBanner(devicesNode));
        root.setBottom(statusBar());
        devNav.fire();

        link.startListening();

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN), devNav::fire);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN), screenNav::fire);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.SHORTCUT_DOWN), filesNav::fire);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.SHORTCUT_DOWN), utilsNav::fire);
        stage.setTitle("SCFT");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    /** Beep once a second for 10 seconds and shake the window; stops early when the other side turns it off. */
    private void ring(boolean on) {
        if (ringing != null) ringing.stop();
        ringing = null;
        if (!on) return;
        stage.toFront();
        ringing = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            Toolkit.getDefaultToolkit().beep();
            TranslateTransition shake = new TranslateTransition(Duration.millis(50), root);
            shake.setByX(10);
            shake.setAutoReverse(true);
            shake.setCycleCount(6);
            shake.play();
        }));
        ringing.setCycleCount(10);
        ringing.play();
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(Main.class);
    }

    private static void applyTheme(boolean isDark) {
        Application.setUserAgentStylesheet((isDark ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet());
    }

    /** Every view scrolls, so nothing is clipped at the 900x600 minimum window size. */
    private static Node wrap(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("view-scroll");
        return sp;
    }

    private Node brand() {
        Label l = new Label("SCFT", new FontIcon("fth-cast"));
        l.getStyleClass().add("title-3");
        l.setPadding(new Insets(0, 0, 16, 8));
        return l;
    }

    private ToggleButton navButton(String text, String icon, String shortcut, ToggleGroup group, Node view) {
        ToggleButton b = new ToggleButton(text, new FontIcon(icon));
        b.setToggleGroup(group);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().add("nav-btn");
        b.setTooltip(new Tooltip(text + " (" + shortcut + ")"));
        b.setOnAction(e -> {
            if (!b.isSelected()) b.setSelected(true); // keep one always selected
            root.setCenter(view);
            current.set(view);
            screenView.setActive(view == screenNode); // animate the mirror only while it is on screen
        });
        return b;
    }

    /**
     * Red banner under the top edge. Port problems show on every screen with a retry button.
     * Connection errors show on the Devices screen next to the form, so the banner only repeats them elsewhere.
     */
    private Node errorBanner(Node devices) {
        Label msg = new Label();
        msg.textProperty().bind(Bindings.when(link.listenErrorProperty().isNotEmpty())
                .then(link.listenErrorProperty())
                .otherwise(link.errorProperty()));
        msg.setWrapText(true);
        msg.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(msg, Priority.ALWAYS);

        Button retry = new Button("Thử lại", new FontIcon("fth-refresh-cw"));
        retry.setOnAction(e -> link.retryListen());
        retry.visibleProperty().bind(link.listenErrorProperty().isNotEmpty());
        retry.managedProperty().bind(retry.visibleProperty());

        Button close = new Button("Đóng", new FontIcon("fth-x"));
        close.setOnAction(e -> link.errorProperty().set(""));
        close.visibleProperty().bind(link.listenErrorProperty().isEmpty());
        close.managedProperty().bind(close.visibleProperty());

        HBox bar = new HBox(12, new FontIcon("fth-alert-triangle"), msg, retry, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.getStyleClass().add("error-banner");
        bar.visibleProperty().bind(link.listenErrorProperty().isNotEmpty()
                .or(link.errorProperty().isNotEmpty().and(current.isNotEqualTo(devices))));
        bar.managedProperty().bind(bar.visibleProperty());
        return bar;
    }

    private Node statusBar() {
        FontIcon dot = new FontIcon("fth-circle");
        dot.getStyleClass().add("status-icon");
        Label state = new Label();
        state.textProperty().bind(link.statusTextProperty());
        link.connectedProperty().addListener((o, a, c) -> {
            dot.setIconLiteral(c ? "fth-check-circle" : "fth-circle");
            dot.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("on"), c);
        });
        HBox bar = new HBox(8, dot, state);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 16, 6, 16));
        bar.getStyleClass().add("statusbar");
        return bar;
    }

    @Override
    public void stop() {
        link.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
