package scft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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

        // This device: address + code for the other side to enter
        Label t1 = new Label("Máy này (chờ kết nối)");
        t1.getStyleClass().add("title-4");
        Label addr = new Label();
        addr.textProperty().bind(link.localAddressProperty().concat(":" + LinkService.PORT));
        addr.getStyleClass().add("pair-addr");
        Label code = new Label();
        code.textProperty().bind(link.pairingCodeProperty());
        code.getStyleClass().add("pair-code");
        Label hint = new Label("Trên thiết bị kia, nhập địa chỉ IP và mã này. Hai thiết bị phải cùng mạng.");
        hint.setWrapText(true);
        hint.getStyleClass().add("text-muted");
        VBox host = new VBox(8, t1, new Label("Địa chỉ"), addr, new Label("Mã ghép nối"), code, hint);
        host.getStyleClass().add("card");
        host.setPadding(new Insets(20));
        host.setMaxWidth(520);

        // Connect to another device
        Label t2 = new Label("Kết nối tới thiết bị khác");
        t2.getStyleClass().add("title-4");
        TextField ip = new TextField();
        ip.setPromptText("Địa chỉ IP, ví dụ 192.168.1.20");
        Label ipL = new Label("Địa chỉ IP");
        ipL.setLabelFor(ip);
        TextField pin = new TextField();
        pin.setPromptText("Mã ghép nối, ví dụ 482 915");
        Label pinL = new Label("Mã ghép nối");
        pinL.setLabelFor(pin);
        Button go = new Button("Kết nối", new FontIcon("fth-link"));
        go.getStyleClass().add("accent");
        go.setDefaultButton(true);
        go.disableProperty().bind(link.connectedProperty()
                .or(ip.textProperty().isEmpty()).or(pin.textProperty().isEmpty()));
        go.setOnAction(e -> link.connectTo(ip.getText(), pin.getText()));
        Label err = new Label();
        err.textProperty().bind(link.errorProperty());
        err.getStyleClass().add("danger");
        err.setWrapText(true);
        err.managedProperty().bind(err.textProperty().isNotEmpty());
        err.visibleProperty().bind(err.textProperty().isNotEmpty());
        VBox join = new VBox(8, t2, ipL, ip, pinL, pin, go, err);
        join.getStyleClass().add("card");
        join.setPadding(new Insets(20));
        join.setMaxWidth(520);

        // Connected device
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

        VBox left = new VBox(16, host, join);
        getChildren().addAll(h, dev, new HBox(20, left));
    }
}
