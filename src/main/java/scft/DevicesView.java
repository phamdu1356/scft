package scft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class DevicesView extends VBox {
    public DevicesView(LinkService link) {
        super(20);
        setPadding(new Insets(28));

        Label h = new Label("Thiết bị");
        h.getStyleClass().add("title-2");

        // Pairing card
        Label step = new Label("Ghép nối điện thoại");
        step.getStyleClass().add("title-4");
        Label code = new Label();
        code.textProperty().bind(link.pairingCodeProperty());
        code.getStyleClass().add("pair-code");
        Label hint = new Label("Mở SCFT trên điện thoại, chọn \"Ghép nối\" và nhập mã này. "
                + "Hai thiết bị phải cùng mạng Wi-Fi.");
        hint.setWrapText(true);
        hint.getStyleClass().add("text-muted");
        Button demo = new Button("Kết nối thử (mô phỏng)", new FontIcon("fth-link"));
        demo.getStyleClass().add("accent");
        demo.setOnAction(e -> link.connect());
        VBox pair = new VBox(10, step, code, hint, demo);
        pair.getStyleClass().add("card");
        pair.setPadding(new Insets(20));
        pair.setMaxWidth(520);

        // Connected device card
        Label name = new Label();
        name.textProperty().bind(link.deviceNameProperty());
        name.getStyleClass().add("title-4");
        Label sub = new Label();
        sub.textProperty().bind(link.statusTextProperty());
        sub.getStyleClass().add("text-muted");
        Button off = new Button("Ngắt kết nối", new FontIcon("fth-x"));
        off.getStyleClass().add("danger");
        off.setOnAction(e -> link.disconnect());
        off.disableProperty().bind(link.connectedProperty().not());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox dev = new HBox(14, new FontIcon("fth-smartphone"), new VBox(2, name, sub), sp, off);
        dev.setAlignment(Pos.CENTER_LEFT);
        dev.getStyleClass().add("card");
        dev.setPadding(new Insets(16, 20, 16, 20));
        dev.setMaxWidth(520);

        demo.disableProperty().bind(link.connectedProperty());
        getChildren().addAll(h, pair, dev);
    }
}
