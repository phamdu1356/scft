package scft;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final BooleanProperty connecting = new SimpleBooleanProperty(false);
    private final StringProperty deviceName = new SimpleStringProperty("—");
    private final StringProperty statusText = new SimpleStringProperty("Chưa kết nối thiết bị");
    private final StringProperty error = new SimpleStringProperty("");
    /** Non-empty when the listening port could not be opened (persistent until a retry succeeds). */
    private final StringProperty listenError = new SimpleStringProperty("");
    private final BooleanProperty mirroring = new SimpleBooleanProperty(false);
    private final ObjectProperty<Direction> direction = new SimpleObjectProperty<>(Direction.PHONE_TO_PC);
    private final ObservableList<TransferItem> transfers = FXCollections.observableArrayList();
    private final StringProperty pairingCode = new SimpleStringProperty(newCode());
    private final StringProperty localAddress = new SimpleStringProperty(findLocalIp());

    private final String myName = System.getProperty("user.name", "SCFT") + "@" + hostName();
    private final Path inbox = Paths.get(System.getProperty("user.home"), "Downloads", "SCFT");

    /** Outgoing files go one at a time over the shared socket. */
    private final ExecutorService sendQueue = Executors.newSingleThreadExecutor(r -> {
        Thread th = new Thread(r, "scft-send");
        th.setDaemon(true);
        return th;
    });

    private volatile Socket peer;
    private volatile DataOutputStream out;
    private ServerSocket server;

    public BooleanProperty connectedProperty() { return connected; }
    public BooleanProperty connectingProperty() { return connecting; }
    public StringProperty deviceNameProperty() { return deviceName; }
    public StringProperty statusTextProperty() { return statusText; }
    public StringProperty errorProperty() { return error; }
    public StringProperty listenErrorProperty() { return listenError; }
    public BooleanProperty mirroringProperty() { return mirroring; }
    public ObjectProperty<Direction> directionProperty() { return direction; }
    public ObservableList<TransferItem> transfers() { return transfers; }
    public StringProperty pairingCodeProperty() { return pairingCode; }
    public StringProperty localAddressProperty() { return localAddress; }
    public Path inbox() { return inbox; }

    // ---- listening side ----

    public void startListening() {
        if (server != null && !server.isClosed()) return; // already listening
        Thread t = new Thread(() -> {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket(PORT);
                server = ss;
                fx(() -> listenError.set(""));
                while (!ss.isClosed()) handshakeIncoming(ss.accept());
            } catch (IOException e) {
                if (ss == null) {
                    String msg = "Không mở được cổng " + PORT + " vì đang bị ứng dụng khác dùng. "
                            + "Đóng ứng dụng đó rồi bấm Thử lại.";
                    System.err.println("SCFT: " + e);
                    fx(() -> listenError.set(msg));
                } else if (!ss.isClosed()) {
                    System.err.println("SCFT: accept failed: " + e);
                }
            }
        }, "scft-listen");
        t.setDaemon(true);
        t.start();
    }

    /** Try to open the listening port again after a failure (for the "Thử lại" button). */
    public void retryListen() {
        startListening();
    }

    public void stop() {
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        disconnect();
        sendQueue.shutdownNow();
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

    /** Connect to the other device. Ignored while a connection is already being made or active. */
    public void connectTo(String host, String code) {
        if (connected.get() || connecting.get()) return;
        String h = host.trim(), c = code.trim();
        connecting.set(true);
        error.set("");
        Thread t = new Thread(() -> {
            Socket s = new Socket();
            String msg = null;
            try {
                s.connect(new InetSocketAddress(h, PORT), 5000);
                s.setSoTimeout(5000);
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                o.writeUTF(MAGIC); o.writeUTF(c); o.writeUTF(myName);
                o.flush();
                String reply = in.readUTF(), name = in.readUTF();
                if (!reply.equals("OK")) {
                    s.close();
                    msg = switch (reply) {
                        case "CODE" -> "Mã ghép nối chưa đúng. Kiểm tra lại mã trên thiết bị kia.";
                        case "BUSY" -> "Thiết bị kia đang kết nối với máy khác. Ngắt kết nối đó rồi thử lại.";
                        default -> "Hai máy đang dùng phiên bản SCFT không tương thích. Hãy cập nhật cả hai.";
                    };
                } else {
                    s.setSoTimeout(0);
                    adopt(s, in, o, name);
                }
            } catch (IOException e) {
                try { s.close(); } catch (IOException ignored) { }
                System.err.println("SCFT connect: " + e);
                msg = friendly(e, h);
            } finally {
                String m = msg;
                fx(() -> { connecting.set(false); if (m != null) error.set(m); });
            }
        }, "scft-connect");
        t.setDaemon(true);
        t.start();
    }

    /** Vietnamese, user-facing wording for a socket failure. Technical detail goes to stderr. */
    private static String friendly(IOException e, String host) {
        if (e instanceof SocketTimeoutException)
            return "Hết thời gian chờ khi kết nối tới " + host + ". Kiểm tra hai máy cùng mạng "
                    + "và tường lửa có đang chặn cổng " + PORT + " không.";
        if (e instanceof NoRouteToHostException)
            return "Không tới được " + host + ". Kiểm tra hai máy cùng mạng Wi-Fi hoặc dây mạng.";
        if (e instanceof ConnectException)
            return "Không kết nối được tới " + host + ". Kiểm tra địa chỉ IP, SCFT trên máy kia "
                    + "đang mở, và hai máy cùng mạng.";
        if (e instanceof UnknownHostException)
            return "Địa chỉ IP không hợp lệ: " + host;
        if (e instanceof EOFException)
            return "Thiết bị kia đã đóng kết nối giữa chừng. Thử lại sau ít giây.";
        return "Không kết nối được tới " + host + ". Vui lòng thử lại.";
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
        fx(() -> { transfers.add(0, t); t.active.set(true); t.status.set("Đang nhận"); });
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
            try { Files.deleteIfExists(dest); } catch (IOException ignored) { } // drop the partial file
            fail(t, "Nhận thất bại: kết nối bị gián đoạn");
            throw e;
        }
        Path saved = dest;
        fx(() -> {
            t.progress.set(1);
            t.savedTo.set(saved);
            t.active.set(false);
            t.status.set("Đã lưu: " + saved.getFileName());
        });
    }

    /** Queue a file for sending. Files are sent one after another; the rest show "Đang chờ". */
    public void send(File f) {
        DataOutputStream o = out;
        if (!connected.get() || o == null) {
            error.set("Hãy kết nối một thiết bị trước khi gửi tệp.");
            return;
        }
        if (f.isDirectory()) {
            error.set("Chỉ gửi được tệp, không gửi được thư mục: " + f.getName());
            return;
        }
        TransferItem t = new TransferItem(f.getName(), f.length(), Direction.PC_TO_PHONE);
        t.active.set(true);
        transfers.add(0, t);
        sendQueue.execute(() -> doSend(t, f, o));
    }

    private void doSend(TransferItem t, File f, DataOutputStream o) {
        if (t.cancelled) return;
        fx(() -> t.status.set("Đang gửi"));
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
                        if (t.cancelled) return; // link was closed; the socket is being dropped anyway
                        o.write(buf, 0, n);
                        sent += n;
                        double p = sent / (double) Math.max(1, f.length());
                        fx(() -> t.progress.set(Math.min(1, p)));
                    }
                }
                o.flush();
            }
            fx(() -> {
                if (t.active.get()) {
                    t.progress.set(1);
                    t.active.set(false);
                    t.status.set("Hoàn tất");
                }
            });
        } catch (IOException e) {
            if (t.cancelled) return;
            System.err.println("SCFT send: " + e);
            fail(t, "Gửi thất bại: kết nối bị gián đoạn");
            closePeer();
        }
    }

    /** Mark an active transfer as failed with a Vietnamese message (no-op if already finished). */
    private void fail(TransferItem t, String msg) {
        fx(() -> {
            if (t.active.get()) {
                t.active.set(false);
                t.status.set(msg);
            }
        });
    }

    public boolean hasActiveTransfers() {
        return transfers.stream().anyMatch(t -> t.active.get());
    }

    /** Remove finished rows (done, failed or cancelled) from the list. Call on the FX thread. */
    public void clearFinished() {
        transfers.removeIf(t -> !t.active.get());
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
            for (TransferItem t : transfers) {
                if (t.active.get()) {
                    t.cancelled = true;
                    t.active.set(false);
                    t.status.set("Đã hủy do ngắt kết nối");
                }
            }
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
        statusText.set(mirroring.get() ? "Đang chiếu thử (mô phỏng): " + direction.get()
                : "Đã kết nối: " + deviceName.get());
    }

    /** Stop the simulated mirror, e.g. when the direction changes while it is running. */
    public void stopMirroring() {
        if (!mirroring.get()) return;
        mirroring.set(false);
        statusText.set(connected.get() ? "Đã kết nối: " + deviceName.get() + " (đã dừng chiếu khi đổi chiều)"
                : "Chưa kết nối thiết bị");
    }

    /** Make a new pairing code (e.g. when the old one may have been seen by someone else). */
    public void regeneratePairingCode() {
        pairingCode.set(newCode());
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

    /** Returns the LAN IPv4 address, or "" when none is found (the UI explains what to do). */
    private static String findLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (SocketException ignored) { }
        return "";
    }
}
