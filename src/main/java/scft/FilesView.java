package scft;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FilesView extends VBox {
    private final LinkService link;

    public FilesView(LinkService link, Runnable goDevices) {
        super(16);
        this.link = link;
        setPadding(new Insets(28));

        Label h = new Label("Tệp");
        h.getStyleClass().add("title-2");

        Label dirLabel = new Label("Tệp nhận được lưu tại: " + link.inbox());
        dirLabel.setWrapText(true);
        dirLabel.getStyleClass().add("text-muted");
        Button openInbox = new Button("Mở thư mục", new FontIcon("fth-folder"));
        openInbox.setOnAction(e -> {
            try {
                Files.createDirectories(link.inbox());
            } catch (java.io.IOException ex) {
                link.errorProperty().set("Không tạo được thư mục nhận tệp: " + link.inbox());
                return;
            }
            openPath(link.inbox());
        });
        HBox dirRow = new HBox(12, dirLabel, openInbox);
        dirRow.setAlignment(Pos.CENTER_LEFT);

        // Drop zone: disabled until a device is connected, with the reason shown inside it.
        Label dz = new Label(null, new FontIcon("fth-upload-cloud"));
        dz.textProperty().bind(Bindings.when(link.connectedProperty())
                .then("Kéo tệp vào đây hoặc bấm để chọn")
                .otherwise("Kết nối một thiết bị để gửi tệp"));
        dz.setWrapText(true);
        dz.setContentDisplay(ContentDisplay.TOP);
        dz.setGraphicTextGap(10);
        VBox zone = new VBox(dz);
        zone.setAlignment(Pos.CENTER);
        zone.setMinHeight(130);
        zone.getStyleClass().add("dropzone");
        zone.setFocusTraversable(true);
        zone.disableProperty().bind(link.connectedProperty().not());
        zone.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            zone.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover-drop"), true);
            e.consume();
        });
        zone.setOnDragExited((DragEvent e) ->
                zone.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover-drop"), false));
        zone.setOnDragDropped(e -> {
            List<File> fs = e.getDragboard().getFiles();
            if (fs != null) fs.forEach(link::send);
            e.setDropCompleted(fs != null);
            e.consume();
        });
        Runnable pick = () -> {
            List<File> fs = new FileChooser().showOpenMultipleDialog(zone.getScene().getWindow());
            if (fs != null) fs.forEach(link::send);
        };
        zone.setOnMouseClicked(e -> pick.run());
        zone.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) pick.run();
        });

        Button toDevices = new Button("Đi tới Thiết bị", new FontIcon("fth-smartphone"));
        toDevices.setOnAction(e -> goDevices.run());
        toDevices.visibleProperty().bind(link.connectedProperty().not());
        toDevices.managedProperty().bind(toDevices.visibleProperty());

        // Transfer history
        Label histTitle = new Label("Lịch sử truyền");
        histTitle.getStyleClass().add("title-4");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button clear = new Button("Xóa mục đã xong", new FontIcon("fth-trash-2"));
        clear.setOnAction(e -> link.clearFinished());
        HBox histHead = new HBox(12, histTitle, sp, clear);
        histHead.setAlignment(Pos.CENTER_LEFT);

        ListView<TransferItem> list = new ListView<>(link.transfers());
        list.setPlaceholder(new Label("Chưa có tệp nào được truyền"));
        list.setCellFactory(v -> new TransferCell());
        list.setMinHeight(160);
        list.setPrefHeight(240);

        getChildren().addAll(h, dirRow, zone, toDevices, histHead, list);
    }

    private void openPath(Path p) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                link.errorProperty().set("Máy này không cho SCFT mở tệp. Hãy mở thư mục " + p + " bằng trình quản lý tệp.");
                return;
            }
            Desktop.getDesktop().open(p.toFile());
        } catch (Exception e) {
            System.err.println("SCFT open: " + e);
            link.errorProperty().set("Không mở được " + p.getFileName() + ". Hãy mở thư mục này bằng trình quản lý tệp.");
        }
    }

    /** One row per transfer. Nodes are created once; bindings are swapped when the row is reused. */
    private class TransferCell extends ListCell<TransferItem> {
        private final FontIcon icon = new FontIcon();
        private final Label name = new Label();
        private final Label dirText = new Label();
        private final ProgressBar bar = new ProgressBar();
        private final Label meta = new Label();
        private final Button open = new Button("Mở tệp", new FontIcon("fth-external-link"));
        private final VBox mid = new VBox(4, name, dirText, bar, meta);
        private final HBox row = new HBox(12, icon, mid, open);
        private TransferItem shown;

        TransferCell() {
            name.getStyleClass().add("text-bold");
            dirText.getStyleClass().add("text-muted");
            bar.setMaxWidth(Double.MAX_VALUE);
            meta.getStyleClass().add("text-muted");
            HBox.setHgrow(mid, Priority.ALWAYS);
            mid.setMaxWidth(Double.MAX_VALUE);
            row.setAlignment(Pos.CENTER_LEFT);
            open.setOnAction(e -> {
                if (shown != null && shown.savedTo.get() != null) openPath(shown.savedTo.get());
            });
        }

        @Override protected void updateItem(TransferItem t, boolean empty) {
            super.updateItem(t, empty);
            if (shown != null) {
                bar.progressProperty().unbind();
                meta.textProperty().unbind();
                open.visibleProperty().unbind();
            }
            shown = empty ? null : t;
            if (empty || t == null) { setGraphic(null); return; }

            boolean in = t.direction == Direction.PHONE_TO_PC;
            icon.setIconLiteral(in ? "fth-arrow-down-circle" : "fth-arrow-up-circle");
            name.setText(t.name);
            dirText.setText(in ? "Nhận từ điện thoại" : "Gửi tới điện thoại");
            bar.progressProperty().bind(t.progress);
            meta.textProperty().bind(Bindings.createStringBinding(() -> {
                        String s = t.status.get()
                                + "  ·  " + TransferItem.human(t.bytes)
                                + "  ·  " + Math.round(Math.min(1, t.progress.get()) * 100) + "%";
                        if (t.active.get() && t.speed.get() > 0) {
                            s += "  ·  " + TransferItem.rate(t.speed.get())
                                    + "  ·  còn " + TransferItem.eta(t.bytes - t.done.get(), t.speed.get());
                        }
                        if (!t.sha256.get().isEmpty()) {
                            s += "  ·  SHA-256 " + t.sha256.get().substring(0, 12) + "…";
                        }
                        return s;
                    },
                    t.status, t.progress, t.speed, t.active, t.done, t.sha256));
            open.visibleProperty().bind(t.savedTo.isNotNull());
            open.managedProperty().bind(open.visibleProperty());
            setGraphic(row);
        }
    }
}
