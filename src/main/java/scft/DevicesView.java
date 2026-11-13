package scft;

import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.regex.Pattern;

public class DevicesView extends VBox {
    /** IPv4 address, optionally followed by ":port" (needed only when the other side uses another port). */
    private static final Pattern ADDR = Pattern.compile(
            "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(:\\d{1,5})?");

    public DevicesView(LinkService link) {
        super(20);
        setPadding(new Insets(28));

        Label h = new Label("Thiết bị");
        h.getStyleClass().add("title-2");

        // ---- Status: one card, either the connected device or a neutral empty state ----
        Label name = new Label();
        name.textProperty().bind(link.deviceNameProperty());
        name.getStyleClass().add("title-4");
        Label sub = new Label();
        sub.textProperty().bind(link.statusTextProperty());
        sub.getStyleClass().add("text-muted");
        Button off = new Button("Ngắt kết nối", new FontIcon("fth-x"));
        off.getStyleClass().add("danger");
        off.setOnAction(e -> confirmDisconnect(link, off));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox devCard = new HBox(14, new FontIcon("fth-smartphone"), new VBox(2, name, sub), sp, off);
        devCard.setAlignment(Pos.CENTER_LEFT);
        devCard.getStyleClass().add("card");
        devCard.setPadding(new Insets(16, 20, 16, 20));
        devCard.visibleProperty().bind(link.connectedProperty());
        devCard.managedProperty().bind(devCard.visibleProperty());

        Label emptyT = new Label("Chưa có thiết bị nào được kết nối");
        emptyT.getStyleClass().add("title-4");
        Label emptyHint = new Label("Ghép nối bằng một trong hai cách: nhập địa chỉ và mã ở thẻ "
                + "\"Máy này\" trên thiết bị kia, hoặc nhập địa chỉ và mã của thiết bị kia ở thẻ bên phải.");
        emptyHint.setWrapText(true);
        emptyHint.getStyleClass().add("text-muted");
        VBox emptyCard = new VBox(6, emptyT, emptyHint);
        emptyCard.getStyleClass().add("card");
        emptyCard.setPadding(new Insets(16, 20, 16, 20));
        emptyCard.visibleProperty().bind(link.connectedProperty().not());
        emptyCard.managedProperty().bind(emptyCard.visibleProperty());

        // ---- This device: address + code for the other side to enter ----
        Label t1 = new Label("Máy này (chờ kết nối)");
        t1.getStyleClass().add("title-4");

        TextField addr = readOnlyField("pair-addr");
        addr.textProperty().bind(Bindings.createStringBinding(
                () -> link.localAddressProperty().get().isEmpty() ? "" : link.localAddressProperty().get() + ":" + LinkService.PORT,
                link.localAddressProperty()));
        Label addrL = new Label("Địa chỉ của máy này");
        addrL.setLabelFor(addr);
        Button copyAddr = copyButton(addr::getText);

        TextField code = readOnlyField("pair-code");
        code.textProperty().bind(link.pairingCodeProperty());
        Label codeL = new Label("Mã ghép nối");
        codeL.setLabelFor(code);
        Button copyCode = copyButton(code::getText);
        Button newCode = new Button("Tạo mã mới", new FontIcon("fth-refresh-cw"));
        newCode.setOnAction(e -> link.regeneratePairingCode());

        HBox addrRow = new HBox(8, addr, copyAddr);
        HBox codeRow = new HBox(8, code, copyCode, newCode);
        addrRow.setAlignment(Pos.CENTER_LEFT);
        codeRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(addr, Priority.ALWAYS);
        HBox.setHgrow(code, Priority.ALWAYS);

        Label hint = new Label("Trên thiết bị kia, nhập địa chỉ IP và mã này. Hai thiết bị phải cùng mạng.");
        hint.setWrapText(true);
        hint.getStyleClass().add("text-muted");
        Label firewall = new Label("Nếu không kết nối được, cho phép SCFT đi qua tường lửa (cổng "
                + LinkService.PORT + ", TCP) rồi thử lại.");
        firewall.setWrapText(true);
        firewall.getStyleClass().add("text-muted");

        VBox ready = new VBox(8, addrL, addrRow, codeL, codeRow, hint, firewall);
        ready.visibleProperty().bind(link.listenErrorProperty().isEmpty().and(link.localAddressProperty().isNotEmpty()));
        ready.managedProperty().bind(ready.visibleProperty());

        Label noNet = warning("Chưa xác định được địa chỉ mạng. Kiểm tra Wi-Fi hoặc dây mạng, "
                + "rồi mở lại SCFT để hiện địa chỉ và mã.");
        noNet.visibleProperty().bind(link.localAddressProperty().isEmpty().and(link.listenErrorProperty().isEmpty()));
        noNet.managedProperty().bind(noNet.visibleProperty());

        Label listenDown = warning("Chưa nhận được kết nối từ thiết bị khác. Xem thông báo phía trên để biết cách xử lý.");
        listenDown.visibleProperty().bind(link.listenErrorProperty().isNotEmpty());
        listenDown.managedProperty().bind(listenDown.visibleProperty());

        VBox host = new VBox(8, t1, ready, noNet, listenDown);
        host.getStyleClass().add("card");
        host.setPadding(new Insets(20));
        host.setPrefWidth(400);

        // ---- Connect to another device ----
        Label t2 = new Label("Kết nối tới thiết bị khác");
        t2.getStyleClass().add("title-4");

        TextField ip = new TextField();
        ip.setPromptText("Ví dụ 192.168.1.20");
        Label ipL = new Label("Địa chỉ IP của thiết bị kia");
        ipL.setLabelFor(ip);
        BooleanBinding ipOk = Bindings.createBooleanBinding(
                () -> ADDR.matcher(ip.getText().trim()).matches(), ip.textProperty());
        Label ipErr = warning("Địa chỉ IP cần có dạng 192.168.1.20 (hoặc 192.168.1.20:45455 nếu máy kia dùng cổng khác)");
        ipErr.visibleProperty().bind(ip.textProperty().isNotEmpty().and(ipOk.not()));
        ipErr.managedProperty().bind(ipErr.visibleProperty());

        TextField pin = new TextField();
        pin.setPromptText("Ví dụ 482 915");
        // Only digits and a single optional space in the middle, at most 7 characters ("482 915").
        pin.setTextFormatter(new TextFormatter<String>(c -> {
            String n = c.getControlNewText();
            return n.length() <= 7 && n.matches("[0-9 ]*") ? c : null;
        }));
        Label pinL = new Label("Mã ghép nối trên thiết bị kia");
        pinL.setLabelFor(pin);
        BooleanBinding pinOk = Bindings.createBooleanBinding(
                () -> pin.getText().replace(" ", "").length() == 6, pin.textProperty());
        Label pinErr = warning("Mã gồm đúng 6 chữ số, ví dụ 482 915");
        pinErr.visibleProperty().bind(pin.textProperty().isNotEmpty().and(pinOk.not()));
        pinErr.managedProperty().bind(pinErr.visibleProperty());

        Label err = warning("");
        err.textProperty().bind(link.errorProperty());
        err.visibleProperty().bind(link.errorProperty().isNotEmpty());
        err.managedProperty().bind(err.visibleProperty());

        BooleanBinding canConnect = link.connectedProperty().not()
                .and(link.connectingProperty().not())
                .and(ipOk).and(pinOk);

        Button go = new Button();
        go.textProperty().bind(Bindings.when(link.connectingProperty()).then("Đang kết nối…").otherwise("Kết nối"));
        go.graphicProperty().bind(Bindings.when(link.connectingProperty())
                .then((javafx.scene.Node) new ProgressIndicator(-1))
                .otherwise(new FontIcon("fth-link")));
        go.getStyleClass().add("accent");
        go.setDefaultButton(true);
        go.disableProperty().bind(canConnect.not());
        go.setOnAction(e -> link.connectTo(ip.getText(), pin.getText()));

        Label reason = new Label();
        reason.getStyleClass().add("text-muted");
        reason.textProperty().bind(Bindings.createStringBinding(() -> {
            if (link.connectedProperty().get()) return "Đã kết nối. Ngắt kết nối trước nếu muốn ghép nối với máy khác.";
            if (link.connectingProperty().get()) return "";
            if (ip.getText().trim().isEmpty()) return "Nhập địa chỉ IP của thiết bị kia.";
            if (!ipOk.get()) return "Địa chỉ IP chưa đúng.";
            if (pin.getText().trim().isEmpty()) return "Nhập mã 6 chữ số hiển thị trên thiết bị kia.";
            if (!pinOk.get()) return "Mã chưa đủ 6 chữ số.";
            return "";
        }, ip.textProperty(), pin.textProperty(), ipOk, pinOk, link.connectedProperty(), link.connectingProperty()));
        reason.visibleProperty().bind(reason.textProperty().isNotEmpty());
        reason.managedProperty().bind(reason.visibleProperty());

        HBox goRow = new HBox(12, go, reason);
        goRow.setAlignment(Pos.CENTER_LEFT);

        VBox join = new VBox(8, t2, ipL, ip, ipErr, pinL, pin, pinErr, err, goRow);
        join.getStyleClass().add("card");
        join.setPadding(new Insets(20));
        join.setPrefWidth(400);

        // Two cards side by side when wide; wraps to one column at narrow widths.
        FlowPane cards = new FlowPane(20, 20, host, join);

        getChildren().addAll(h, devCard, emptyCard, cards);
    }

    private static TextField readOnlyField(String styleClass) {
        TextField f = new TextField();
        f.setEditable(false);
        f.getStyleClass().add(styleClass);
        f.setFocusTraversable(true);
        return f;
    }

    private static Label warning(String text) {
        Label l = new Label(text, new FontIcon("fth-alert-circle"));
        l.setWrapText(true);
        l.getStyleClass().add("danger");
        return l;
    }

    private static Button copyButton(java.util.function.Supplier<String> text) {
        Button b = new Button("Sao chép", new FontIcon("fth-copy"));
        b.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(text.get());
            Clipboard.getSystemClipboard().setContent(c);
            b.setText("Đã chép");
            PauseTransition back = new PauseTransition(Duration.seconds(1.5));
            back.setOnFinished(ev -> b.setText("Sao chép"));
            back.playFromStart();
        });
        return b;
    }

    /** Warn before dropping a link that still has files in flight. */
    private static void confirmDisconnect(LinkService link, Node owner) {
        if (link.hasActiveTransfers()) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "Đang có tệp chưa truyền xong. Ngắt kết nối bây giờ sẽ hủy các tệp đó. Tiếp tục?",
                    ButtonType.OK, ButtonType.CANCEL);
            a.setTitle("Ngắt kết nối?");
            a.setHeaderText("Đang truyền tệp");
            if (owner.getScene() != null) a.initOwner(owner.getScene().getWindow());
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        link.disconnect();
    }
}
