package scft;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.List;

public class FilesView extends VBox {
    public FilesView(LinkService link) {
        super(16);
        setPadding(new Insets(28));

        Label h = new Label("Tệp & dữ liệu");
        h.getStyleClass().add("title-2");

        Label dirLabel = new Label();
        dirLabel.textProperty().bind(link.directionProperty().asString("Hướng: %s"));
        dirLabel.getStyleClass().add("text-muted");

        // Drop zone
        Label dz = new Label("Kéo tệp vào đây hoặc bấm để chọn", new FontIcon("fth-upload-cloud"));
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
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) pick.run();
        });

        // Transfer list
        ListView<TransferItem> list = new ListView<>(link.transfers());
        list.setPlaceholder(new Label("Chưa có tệp nào được truyền"));
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(TransferItem t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); return; }
                FontIcon ic = new FontIcon(t.direction == Direction.PHONE_TO_PC
                        ? "fth-arrow-down-circle" : "fth-arrow-up-circle");
                Label n = new Label(t.name);
                Label meta = new Label();
                meta.textProperty().bind(t.status.concat("  ·  " + TransferItem.human(t.bytes)));
                meta.getStyleClass().add("text-muted");
                ProgressBar pb = new ProgressBar();
                pb.progressProperty().bind(t.progress);
                pb.setMaxWidth(Double.MAX_VALUE);
                VBox mid = new VBox(4, n, pb, meta);
                HBox.setHgrow(mid, Priority.ALWAYS);
                HBox row = new HBox(12, ic, mid);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        getChildren().addAll(h, dirLabel, zone, list);
    }
}
