package scft;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real LAN transport (TCP). One instance listens on {@value #PORT}; the other connects with
 * the host's IP and the pairing code shown on the host. Files go both ways over the same socket.
 * Screen mirroring is still simulated (see ScreenView).
 */
public class LinkService {
    public static final int PORT = 45454;
    private static final String MAGIC = "SCFT1";
    private static final byte T_FILE = 1, T_BYE = 2;

    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private final StringProperty deviceName = new SimpleStringProperty("—");
    private final StringProperty statusText = new SimpleStringProperty("Chưa kết nối thiết bị");
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanProperty mirroring = new SimpleBooleanProperty(false);
    private final ObjectProperty<Direction> direction = new SimpleObjectProperty<>(Direction.PHONE_TO_PC);
    private final ObservableList<TransferItem> transfers = FXCollections.observableArrayList();
    private final StringProperty pairingCode = new SimpleStringProperty(newCode());
    private final StringProperty localAddress = new SimpleStringProperty(findLocalIp());

    private final String myName = System.getProperty("user.name", "SCFT") + "@" + hostName();
    private final Path inbox = Paths.get(System.getProperty("user.home"), "Downloads", "SCFT");

    private volatile Socket peer;
    private volatile DataOutputStream out;
    private ServerSocket server;

    public BooleanProperty connectedProperty() { return connected; }
    public StringProperty deviceNameProperty() { return deviceName; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty errorProperty() { return error; }
    public BooleanProperty mirroringProperty() { return mirroring; }
    public ObjectProperty<Direction> directionProperty() { return direction; }
    public ObservableList<TransferItem> transfers() { return transfers; }
    public StringProperty pairingCodeProperty() { return pairingCode; }
    public StringProperty localAddressProperty() { return localAddress; }
    public Path inbox() { return inbox; }

    // ---- listening side ----

    public void startListening() {
        Thread t = new Thread(() -> {
            try {
                server = new ServerSocket(PORT);
                while (!server.isClosed()) handshakeIncoming(server.accept());
            } catch (IOException e) {
                if (server == null || !server.isClosed()) fx(() -> error.set("Không mở được cổng " + PORT + ": " + e.getMessage()));
            }
        }, "scft-listen");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        disconnect();
    }

    private void handshakeIncoming(Socket s) {
        try {
            s.setSoTimeout(5000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            String magic = in.readUTF(), code = in.readUTF(), name = in.readUTF();
            String reply = !MAGIC.equals(magic) ? "BAD" : connected.get() ? "BUSY"
                    : !code.replace(" ", "").equals(pairingCode.get().replace(" ", "")) ? "CODE" : "OK";
            o.writeUTF(reply);
            o.writeUTF(myName);
            o.flush();
            if (!reply.equals("OK")) { s.close(); return; }
            s.setSoTimeout(0);
            adopt(s, in, o, name);
        } catch (IOException e) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    // ---- connecting side ----

    public void connectTo(String host, String code) {
        fx(() -> error.set(""));
        Thread t = new Thread(() -> {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(host.trim(), PORT), 5000);
                s.setSoTimeout(5000);
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                o.writeUTF(MAGIC); o.writeUTF(code.trim()); o.writeUTF(myName);
                o.flush();
                String reply = in.readUTF(), name = in.readUTF();
                if (!reply.equals("OK")) {
                    s.close();
                    String msg = switch (reply) {
                        case "CODE" -> "Sai mã ghép nối";
                        case "BUSY" -> "Thiết bị kia đang kết nối với máy khác";
                        default -> "Giao thức không tương thích";
                    };
                    fx(() -> error.set(msg));
                    return;
                }
                s.setSoTimeout(0);
                adopt(s, in, o, name);
            } catch (IOException e) {
                try { s.close(); } catch (IOException ignored) { }
                fx(() -> error.set("Không kết nối được tới " + host.trim() + ": " + e.getMessage()));
            }
        }, "scft-connect");
        t.setDaemon(true);
        t.start();
    }

    // ---- shared ----

    private void adopt(Socket s, DataInputStream in, DataOutputStream o, String name) {
        peer = s;
        out = o;
        fx(() -> {
            connected.set(true);
            deviceName.set(name);
            statusText.set("Đã kết nối: " + name);
            error.set("");
        });
        Thread r = new Thread(() -> readLoop(s, in), "scft-read");
        r.setDaemon(true);
        r.start();
    }

    private void readLoop(Socket s, DataInputStream in) {
        try {
            while (true) {
                byte type = in.readByte();
                if (type == T_BYE) break;
                if (type == T_FILE) receiveFile(in);
                else break; // unknown frame: stream is out of sync, drop the link
            }
        } catch (IOException ignored) {
        } finally {
            if (peer == s) closePeer();
        }
    }

    private void receiveFile(DataInputStream in) throws IOException {
        String raw = in.readUTF();
        long size = in.readLong();
        Path fn = Paths.get(raw).getFileName(); // never trust the sender's path
        String name = fn == null ? "file" : fn.toString();
        TransferItem t = new TransferItem(name, size, Direction.PHONE_TO_PC);
        fx(() -> { transfers.add(0, t); t.status.set("Đang nhận"); });
        Files.createDirectories(inbox);
        Path dest = unique(inbox.resolve(name));
        try (OutputStream f = Files.newOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            long left = size;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new EOFException();
                f.write(buf, 0, n);
                left -= n;
                double p = size == 0 ? 1 : (size - left) / (double) size;
                fx(() -> t.progress.set(p));
            }
        } catch (IOException e) {
            fx(() -> t.status.set("Lỗi"));
            throw e;
        }
        fx(() -> { t.progress.set(1); t.status.set("Đã lưu: " + dest.getFileName()); });
    }

    public void send(File f) {
        DataOutputStream o = out;
        if (!connected.get() || o == null) return;
        TransferItem t = new TransferItem(f.getName(), f.length(), Direction.PC_TO_PHONE);
        fx(() -> { transfers.add(0, t); t.status.set("Đang gửi"); });
        Thread th = new Thread(() -> {
            try {
                synchronized (o) {
                    o.writeByte(T_FILE);
                    o.writeUTF(f.getName());
                    o.writeLong(f.length());
                    try (InputStream in = new FileInputStream(f)) {
                        byte[] buf = new byte[64 * 1024];
                        long sent = 0;
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            o.write(buf, 0, n);
                            sent += n;
                            double p = sent / (double) Math.max(1, f.length());
                            fx(() -> t.progress.set(Math.min(1, p)));
                        }
                    }
                    o.flush();
                }
                fx(() -> { t.progress.set(1); t.status.set("Hoàn tất"); });
            } catch (IOException e) {
                fx(() -> t.status.set("Lỗi: " + e.getMessage()));
                closePeer();
            }
        }, "scft-send");
        th.setDaemon(true);
        th.start();
    }

    public void disconnect() {
        DataOutputStream o = out;
        if (o != null) {
            try { synchronized (o) { o.writeByte(T_BYE); o.flush(); } } catch (IOException ignored) { }
        }
        closePeer();
    }

    private void closePeer() {
        Socket s = peer;
        peer = null;
        out = null;
        if (s != null) try { s.close(); } catch (IOException ignored) { }
        fx(() -> {
            mirroring.set(false);
            connected.set(false);
            deviceName.set("—");
            statusText.set("Chưa kết nối thiết bị");
            pairingCode.set(newCode());
        });
    }

    public void toggleMirroring() {
        if (!connected.get()) return;
        mirroring.set(!mirroring.get());
        statusText.set(mirroring.get() ? "Đang chiếu (mô phỏng): " + direction.get()
                : "Đã kết nối: " + deviceName.get());
    }

    // ---- helpers ----

    /** Run on the FX thread when a toolkit exists; otherwise inline (headless tests). */
    private static void fx(Runnable r) {
        try { Platform.runLater(r); } catch (IllegalStateException e) { r.run(); }
    }

    private static Path unique(Path p) {
        if (!Files.exists(p)) return p;
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n, ext = dot > 0 ? n.substring(dot) : "";
        for (int i = 1; ; i++) {
            Path c = p.resolveSibling(base + " (" + i + ")" + ext);
            if (!Files.exists(c)) return c;
        }
    }

    private static String newCode() {
        int n = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return (n / 1000) + " " + (n % 1000);
    }

    private static String hostName() {
        try { return InetAddress.getLocalHost().getHostName(); } catch (IOException e) { return "pc"; }
    }

    private static String findLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (SocketException ignored) { }
        return "không rõ";
    }
}
