package scft;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

/** Application shell: sidebar navigation + swappable content + status bar. */
public class Main extends Application {

    private final LinkService link = new LinkService();
    private final BorderPane root = new BorderPane();
    private final BooleanProperty dark = new SimpleBooleanProperty(false);

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        dark.addListener((o, a, isDark) -> Application.setUserAgentStylesheet(
                (isDark ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet()));

        Node devices = new DevicesView(link);
        Node screen = new ScreenView(link);
        Node files = new FilesView(link);

        ToggleGroup nav = new ToggleGroup();
        ToggleButton first = navButton("Thiết bị", "fth-smartphone", nav, devices);
        VBox sidebar = new VBox(4,
                brand(),
                first,
                navButton("Màn hình", "fth-monitor", nav, screen),
                navButton("Tệp & dữ liệu", "fth-repeat", nav, files));
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.setPrefWidth(220);

        ToggleButton theme = new ToggleButton("Giao diện tối", new FontIcon("fth-moon"));
        theme.setMaxWidth(Double.MAX_VALUE);
        theme.selectedProperty().bindBidirectional(dark);
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        sidebar.getChildren().addAll(grow, theme);

        root.setLeft(sidebar);
        root.setBottom(statusBar());
        first.fire();

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        stage.setTitle("SCFT");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    private Node brand() {
        Label l = new Label("SCFT", new FontIcon("fth-cast"));
        l.getStyleClass().add("title-3");
        l.setPadding(new Insets(0, 0, 16, 8));
        return l;
    }

    private ToggleButton navButton(String text, String icon, ToggleGroup group, Node view) {
        ToggleButton b = new ToggleButton(text, new FontIcon(icon));
        b.setToggleGroup(group);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().add("nav-btn");
        b.setOnAction(e -> {
            if (!b.isSelected()) b.setSelected(true); // keep one always selected
            root.setCenter(view);
        });
        return b;
    }

    private Node statusBar() {
        Label dot = new Label();
        dot.getStyleClass().add("status-dot");
        Label state = new Label();
        state.textProperty().bind(link.statusTextProperty());
        link.connectedProperty().addListener((o, a, c) -> dot.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("on"), c));
        HBox bar = new HBox(8, dot, state);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 16, 6, 16));
        bar.getStyleClass().add("statusbar");
        return bar;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
