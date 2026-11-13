package scft;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * "Tiện ích" tab: two-way helpers that work between two SCFT instances. Each card is one feature.
 * Actions are disabled until a device is connected. Requests that change this PC (open a link, media
 * keys, notifications) run on a worker thread and send back a Reply with the result.
 */
public class UtilsView extends VBox {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int CHAT_MAX = 200;

    private final LinkService link;
    private final Label lastResult = new Label();
    private final ObservableList<String> chat = FXCollections.observableArrayList();
    private final CheckBox autoClip = new CheckBox("Tự đồng bộ văn bản clipboard (hai chiều)");
    private final Label clipInfo = muted("Chưa có nội dung nào được đồng bộ.");
    /** Last clipboard text sent or received. Used to avoid echoing a received text back. */
    private String lastClip = "";
    private final Label findInfo = muted("");
    private final PauseTransition findEnd = new PauseTransition(Duration.seconds(10));
    private final Label notifyInfo = muted("Chưa có thông báo nào từ máy kia.");
    private final ImageView shotView = new ImageView();
    private final Label shotInfo = muted("Chưa có ảnh chụp nào.");
    private long pendingShot;

    public UtilsView(LinkService link) {
        super(20);
        this.link = link;
        setPadding(new Insets(28));

        Label h = new Label("Tiện ích");
        h.getStyleClass().add("title-2");
        Label sub = muted("Thao tác hai chiều với máy đang kết nối. Khi chưa có máy kết nối, các nút bị khóa.");

        lastResult.setWrapText(true);
        lastResult.getStyleClass().add("text-muted");
        lastResult.visibleProperty().bind(lastResult.textProperty().isNotEmpty());
        lastResult.managedProperty().bind(lastResult.visibleProperty());

        findEnd.setOnFinished(e -> findInfo.setText(""));
        shotView.setFitWidth(380);
        shotView.setPreserveRatio(true);

        FlowPane grid = new FlowPane(20, 20,
                statusCard(), clipCard(), chatCard(), findCard(), urlCard(),
                shotCard(), mediaCard(), notifyCard(), filesCard(), historyCard());
        getChildren().addAll(h, sub, lastResult, grid);

        link.addListener(this::onMessage);
        Timeline clipboardPoll = new Timeline(new KeyFrame(Duration.seconds(1), e -> pollClipboard()));
        clipboardPoll.setCycleCount(Animation.INDEFINITE);
        clipboardPoll.play();
    }

    // ---- cards ----

    private VBox statusCard() {
        Label ping = new Label();
        bindText(ping, () -> link.rttMsProperty().get() < 0
                ? "Ping: chưa đo (cần kết nối)"
                : "Ping (RTT): " + link.rttMsProperty().get() + " ms", link.rttMsProperty());
        Label local = new Label();
        bindText(local, () -> battery("Pin máy này", link.localBatteryProperty().get(),
                link.localChargingProperty().get()), link.localBatteryProperty(), link.localChargingProperty());
        Label peer = new Label();
        bindText(peer, () -> battery("Pin máy kia", link.peerBatteryProperty().get(),
                link.peerChargingProperty().get()), link.peerBatteryProperty(), link.peerChargingProperty());
        Label net = new Label();
        bindText(net, () -> "Mạng máy kia: " + (link.peerNetProperty().get().isEmpty()
                ? "—" : link.peerNetProperty().get()), link.peerNetProperty());
        return card("Trạng thái & ping", ping, local, peer, net,
                muted("Ping đo mỗi 2 giây. Pin và mạng được gửi cho nhau mỗi 6 giây. "
                        + "Máy không có pin sẽ hiện \"không rõ\"."));
    }

    private VBox clipCard() {
        autoClip.selectedProperty().addListener((o, was, on) -> {
            if (on) lastClip = currentClipboard(); // do not send whatever was already copied
        });
        Button send = online(button("Gửi clipboard máy này ngay", "fth-clipboard",
                () -> sendClip(currentClipboard(), true)));
        return card("Đồng bộ clipboard", autoClip, send, clipInfo);
    }

    private VBox chatCard() {
        ListView<String> list = new ListView<>(chat);
        list.setPlaceholder(new Label("Chưa có tin nhắn"));
        list.setPrefHeight(180);
        TextField input = new TextField();
        input.setPromptText("Nhập tin nhắn hoặc ghi chú…");
        HBox.setHgrow(input, Priority.ALWAYS);
        Runnable doSend = () -> {
            String t = input.getText().trim();
            if (t.isEmpty()) return;
            if (link.sendControl(new Msg.Chat(t))) {
                chat.add(stamp("Tôi", t));
                trimChat();
                input.clear();
            }
        };
        input.setOnAction(e -> doSend.run());
        input.disableProperty().bind(link.connectedProperty().not());
        Button send = online(button("Gửi", "fth-send", doSend));
        HBox row = new HBox(8, input, send);
        row.setAlignment(Pos.CENTER_LEFT);
        return card("Chat & ghi chú nhanh", list, row);
    }

    private VBox findCard() {
        Button ring = online(button("Báo máy kia (bíp và rung)", "fth-bell",
                () -> link.sendControl(new Msg.Find(true))));
        Button stop = online(button("Dừng báo", "fth-bell-off",
                () -> link.sendControl(new Msg.Find(false))));
        findInfo.setWrapText(true);
        return card("Tìm thiết bị", new HBox(8, ring, stop), findInfo,
                muted("Máy nhận báo sẽ bíp và rung cửa sổ SCFT trong 10 giây."));
    }

    private VBox urlCard() {
        TextField url = new TextField();
        url.setPromptText("https://…");
        Button go = online(button("Mở trên máy kia", "fth-external-link", () -> {
            String u = url.getText().trim();
            try {
                SystemOps.webUrl(u);
            } catch (IllegalArgumentException e) {
                lastResult.setText(e.getMessage());
                return;
            }
            if (link.sendControl(new Msg.Url(u))) {
                lastResult.setText("Đã gửi liên kết tới máy kia, đang chờ kết quả…");
                url.clear();
            }
        }));
        url.setOnAction(e -> go.fire());
        url.disableProperty().bind(link.connectedProperty().not());
        return card("Mở liên kết trên máy kia", url, go,
                muted("Chỉ nhận liên kết http:// hoặc https://."));
    }

    private VBox shotCard() {
        Button req = online(button("Yêu cầu chụp màn hình máy kia", "fth-camera", this::requestShot));
        shotInfo.setWrapText(true);
        return card("Chụp màn hình máy kia", req, shotInfo, shotView,
                muted("Máy kia phải cho phép. Ảnh được lưu vào thư mục nhận tệp."));
    }

    private VBox mediaCard() {
        HBox row = new HBox(8,
                iconButton("fth-skip-back", "Bài trước", SystemOps.MEDIA_PREV),
                iconButton("fth-play", "Phát/tạm dừng", SystemOps.MEDIA_PLAY_PAUSE),
                iconButton("fth-skip-forward", "Bài kế tiếp", SystemOps.MEDIA_NEXT),
                iconButton("fth-volume-1", "Giảm âm lượng", SystemOps.MEDIA_VOL_DOWN),
                iconButton("fth-volume-2", "Tăng âm lượng", SystemOps.MEDIA_VOL_UP),
                iconButton("fth-volume-x", "Tắt/bật tiếng", SystemOps.MEDIA_MUTE));
        return card("Điều khiển media máy kia", row,
                muted("Lệnh chạy trên máy kia. Linux cần playerctl (phát nhạc) và wpctl hoặc amixer (âm lượng)."));
    }

    private VBox notifyCard() {
        TextField title = new TextField("SCFT");
        title.setPromptText("Tiêu đề");
        TextField body = new TextField();
        body.setPromptText("Nội dung thông báo");
        Runnable doSend = () -> {
            String t = title.getText().trim();
            String b = body.getText().trim();
            if (t.isEmpty() && b.isEmpty()) {
                lastResult.setText("Nhập tiêu đề hoặc nội dung thông báo.");
                return;
            }
            if (link.sendControl(new Msg.Notify(t.isEmpty() ? "SCFT" : t, b))) {
                lastResult.setText("Đã gửi thông báo tới máy kia, đang chờ kết quả…");
                body.clear();
            }
        };
        body.setOnAction(e -> doSend.run());
        Button send = online(button("Gửi thông báo", "fth-message-square", doSend));
        return card("Gửi thông báo", title, body, send, notifyInfo);
    }

    private VBox filesCard() {
        Button pick = online(button("Gửi nhiều tệp…", "fth-upload", () -> {
            List<File> files = new FileChooser().showOpenMultipleDialog(getScene().getWindow());
            if (files != null) files.forEach(link::send);
        }));
        return card("Gửi nhiều tệp", pick,
                muted("Các tệp gửi lần lượt, mỗi tệp kèm SHA-256 để kiểm tra. "
                        + "Tốc độ và thời gian còn lại xem ở tab Tệp & dữ liệu."));
    }

    private VBox historyCard() {
        TextField search = new TextField();
        search.setPromptText("Tìm theo nội dung hoặc loại (ví dụ: clipboard, URL)…");
        FilteredList<Activity> filtered = new FilteredList<>(link.activity(), a -> true);
        search.textProperty().addListener((o, was, q) -> {
            String k = q == null ? "" : q.trim().toLowerCase();
            filtered.setPredicate(x -> k.isEmpty() || (x.kind() + " " + x.text()).toLowerCase().contains(k));
        });
        ListView<Activity> list = new ListView<>(filtered);
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(Activity a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : a.line());
            }
        });
        list.setPlaceholder(new Label("Chưa có hoạt động nào"));
        list.setPrefHeight(260);
        Label count = muted("");
        bindText(count, () -> filtered.size() + " / " + link.activity().size() + " mục",
                filtered, Bindings.size(filtered), Bindings.size(link.activity()));
        Button clear = button("Xóa lịch sử", "fth-trash-2", () -> link.activity().clear());
        return card("Lịch sử hoạt động", search, count, list, clear);
    }

    // ---- messages from the other device ----

    private void onMessage(Msg m) {
        switch (m) {
            case Msg.Clip c -> onClip(c.text());
            case Msg.Chat c -> {
                chat.add(stamp("Máy kia", c.text()));
                trimChat();
            }
            case Msg.Find f -> {
                findEnd.stop();
                findInfo.setText(f.on() ? "Máy kia đang tìm thiết bị này (bíp và rung)." : "");
                if (f.on()) findEnd.playFromStart();
            }
            case Msg.Url u -> openRemoteUrl(u.url());
            case Msg.ShotReq r -> askScreenshot(r.id());
            case Msg.ShotResp r -> shotReceived(r);
            case Msg.Media md -> runMedia(md.cmd());
            case Msg.Notify n -> showNotify(n);
            case Msg.Reply r -> lastResult.setText((r.ok() ? "" : "Lỗi: ") + "Máy kia: " + r.text());
            default -> { }
        }
    }

    private void onClip(String text) {
        boolean sync = autoClip.isSelected();
        if (sync) {
            lastClip = text;
            ClipboardContent c = new ClipboardContent();
            c.putString(text);
            Clipboard.getSystemClipboard().setContent(c);
        }
        clipInfo.setText((sync ? "Đã nhận và chép vào clipboard: " : "Đã nhận, nhưng đồng bộ đang tắt nên chưa chép: ")
                + Activity.preview(text));
    }

    private void openRemoteUrl(String url) {
        bg(() -> {
            String text;
            boolean ok;
            try {
                SystemOps.openUrl(url);
                ok = true;
                text = "Đã mở liên kết trên máy này.";
            } catch (IllegalArgumentException | IOException e) {
                ok = false;
                text = e.getMessage();
            }
            link.sendControl(new Msg.Reply(Msg.T_URL, ok, text));
        });
    }

    private void runMedia(int cmd) {
        bg(() -> {
            String err = SystemOps.media(cmd);
            link.sendControl(new Msg.Reply(Msg.T_MEDIA, err == null,
                    err == null ? SystemOps.mediaName(cmd) + " đã thực hiện" : err));
        });
    }

    private void showNotify(Msg.Notify n) {
        notifyInfo.setText("Từ máy kia: " + n.title() + (n.body().isEmpty() ? "" : " — " + n.body()));
        bg(() -> {
            boolean shown = SystemOps.notifyDesktop(n.title(), n.body());
            link.sendControl(new Msg.Reply(Msg.T_NOTIFY, true,
                    shown ? "Đã hiện thông báo trên máy này."
                            : "Đã nhận, hiển thị trong SCFT vì máy này không có notify-send."));
        });
    }

    /** Asks this user before taking a screenshot. The answer goes back as a ShotResp. */
    private void askScreenshot(long id) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Máy kia muốn xem ảnh chụp màn hình máy này. Cho phép gửi ảnh?",
                ButtonType.OK, ButtonType.CANCEL);
        a.setTitle("Yêu cầu chụp màn hình");
        a.setHeaderText("Máy kia yêu cầu chụp màn hình");
        if (getScene() != null) a.initOwner(getScene().getWindow());
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            link.sendControl(new Msg.ShotResp(id, "Người dùng từ chối", null));
            return;
        }
        bg(() -> {
            try {
                link.sendControl(new Msg.ShotResp(id, null, SystemOps.capturePng()));
            } catch (IOException e) {
                link.sendControl(new Msg.ShotResp(id, e.getMessage(), null));
            }
        });
    }

    // ---- actions on this device ----

    private void sendClip(String text, boolean manual) {
        if (text.isEmpty()) {
            if (manual) lastResult.setText("Clipboard không có văn bản để gửi.");
            return;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > Msg.MAX_TEXT) {
            clipInfo.setText("Văn bản quá lớn để đồng bộ (tối đa 1 MB).");
            return;
        }
        lastClip = text;
        if (link.sendControl(new Msg.Clip(text))) clipInfo.setText("Đã gửi: " + Activity.preview(text));
    }

    private void pollClipboard() {
        if (!autoClip.isSelected() || !link.isConnected()) return;
        String now = currentClipboard();
        if (now.isEmpty() || now.equals(lastClip)) return;
        sendClip(now, false);
    }

    private void requestShot() {
        long id = ThreadLocalRandom.current().nextLong();
        if (link.sendControl(new Msg.ShotReq(id))) {
            pendingShot = id;
            shotInfo.setText("Đang chờ máy kia cho phép và gửi ảnh…");
        }
    }

    private void shotReceived(Msg.ShotResp r) {
        if (r.id() != pendingShot) return;
        pendingShot = 0;
        if (r.error() != null) {
            shotInfo.setText("Máy kia không gửi được ảnh: " + r.error());
            return;
        }
        try {
            Files.createDirectories(link.inbox());
            Path p = link.inbox().resolve("screenshot-" + LocalDateTime.now().format(FILE_TS) + ".png");
            Files.write(p, r.png());
            shotView.setImage(new Image(new ByteArrayInputStream(r.png())));
            shotInfo.setText("Đã lưu: " + p);
        } catch (IOException e) {
            shotInfo.setText("Không lưu được ảnh: " + e.getMessage());
        }
    }

    private Button iconButton(String icon, String tip, int cmd) {
        Button b = online(new Button(null, new FontIcon(icon)));
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> {
            if (link.sendControl(new Msg.Media(cmd))) lastResult.setText("Đã gửi lệnh: " + SystemOps.mediaName(cmd));
        });
        return b;
    }

    // ---- small helpers ----

    private static VBox card(String title, Node... body) {
        Label t = new Label(title);
        t.getStyleClass().add("title-4");
        VBox c = new VBox(10);
        c.getChildren().add(t);
        c.getChildren().addAll(body);
        c.getStyleClass().add("card");
        c.setPadding(new Insets(16, 20, 16, 20));
        c.setPrefWidth(420);
        return c;
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add("text-muted");
        return l;
    }

    private static Button button(String text, String icon, Runnable action) {
        Button b = new Button(text, new FontIcon(icon));
        b.setOnAction(e -> action.run());
        return b;
    }

    /** Disables the button while no device is connected. */
    private <T extends Node> T online(T node) {
        if (node instanceof ButtonBase b) b.disableProperty().bind(link.connectedProperty().not());
        return node;
    }

    private static void bindText(Label l, Supplier<String> text, Observable... deps) {
        l.textProperty().bind(Bindings.createStringBinding(text::get, deps));
    }

    private static String battery(String who, int percent, boolean charging) {
        if (percent < 0) return who + ": không rõ (máy không có pin hoặc chưa đọc được)";
        return who + ": " + percent + "%" + (charging ? " · đang sạc" : "");
    }

    private static String stamp(String who, String text) {
        return "[" + LocalTime.now().format(HM) + "] " + who + ": " + text;
    }

    private void trimChat() {
        while (chat.size() > CHAT_MAX) chat.remove(0);
    }

    private static String currentClipboard() {
        String s = Clipboard.getSystemClipboard().getString();
        return s == null ? "" : s;
    }

    /** Runs slow OS work off the FX thread. */
    private static void bg(Runnable r) {
        Thread t = new Thread(r, "scft-util");
        t.setDaemon(true);
        t.start();
    }
}
