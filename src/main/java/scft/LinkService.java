package scft;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Real LAN transport (TCP). One instance listens on {@link #PORT}; the other connects with the host's
 * IP (optionally "ip:port") and the pairing code shown on the host. Everything else (files, clipboard,
 * chat, utilities) travels over the same socket as typed frames (see {@link Msg}).
 *
 * <p>Threads: a read thread per link, a single send queue for files, and a heartbeat timer that sends
 * ping/status and drops a link that has been silent too long. The connecting side reconnects by itself
 * after an unexpected drop; a user disconnect never reconnects.
 */
public class LinkService {
    /** Listening port. Override with -Dscft.port=NNNN to run two instances on one machine. */
    public static final int PORT = Integer.getInteger("scft.port", 45454);
    static final String MAGIC = "SCFT1";
    /** Socket and stream buffer size. */
    static final int BUF = 256 * 1024;
    private static final long BEAT_MS = 2000;
    private static final long DEAD_NS = 15_000_000_000L;
    private static final int MAX_RETRY = 8;
    private static final int LOG_MAX = 500;

    /** Connecting side only: where to reconnect after an unexpected drop. */
    private record Target(String host, int port, String code) {}

    /** Computes a smoothed speed and allows a UI update at most every 200 ms. */
    static final class Meter {
        private final long start = System.nanoTime();
        private long lastNs = start, lastBytes;
        private double bps;

        boolean tick(long done) {
            long now = System.nanoTime();
            if (now - lastNs < 200_000_000L) return false;
            double inst = (done - lastBytes) * 1e9 / (now - lastNs);
            bps = bps == 0 ? inst : 0.7 * bps + 0.3 * inst;
            lastNs = now;
            lastBytes = done;
            return true;
        }

        double bps() { return bps; }
    }

    /** A file being received. out is null once the write failed (the bytes are then discarded). */
    private static final class Incoming {
        final TransferItem item;
        final Path dest;
        OutputStream out;
        final MessageDigest md = sha256();
        final Meter meter = new Meter();
        final long size;
        long got;

        Incoming(TransferItem item, Path dest, OutputStream out, long size) {
            this.item = item;
            this.dest = dest;
            this.out = out;
            this.size = size;
        }
    }

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

    // heartbeat results (ping, batteries, network); all set on the FX thread
    private final LongProperty rttMs = new SimpleLongProperty(-1);
    private final IntegerProperty localBattery = new SimpleIntegerProperty(-1);
    private final BooleanProperty localCharging = new SimpleBooleanProperty(false);
    private final IntegerProperty peerBattery = new SimpleIntegerProperty(-1);
    private final BooleanProperty peerCharging = new SimpleBooleanProperty(false);
    private final StringProperty peerNet = new SimpleStringProperty("");

    private final ObservableList<Activity> activity = FXCollections.observableArrayList();
    private final List<Consumer<Msg>> listeners = new CopyOnWriteArrayList<>();

    private final int port;
    private final String myName = System.getProperty("user.name", "SCFT") + "@" + hostName();
    private final Path inbox = Paths.get(System.getProperty("user.home"), "Downloads", "SCFT");
    private final Object writeLock = new Object();
    /** Sent files waiting for the receiver's SHA-256 verdict (FIFO, the receiver answers in order). */
    private final Queue<TransferItem> awaitingAck = new ConcurrentLinkedQueue<>();

    /** Outgoing files go one at a time over the shared socket. */
    private final ExecutorService sendQueue = Executors.newSingleThreadExecutor(daemon("scft-send"));
    private final ScheduledExecutorService beat = Executors.newSingleThreadScheduledExecutor(daemon("scft-beat"));

    private volatile Socket peer;
    private volatile DataOutputStream out;
    private volatile long lastRx;
    private volatile Target target;
    private volatile int retries;
    private int beats;
    private ServerSocket server;
    private Incoming incoming; // touched only by the read thread

    public LinkService() {
        this(PORT);
    }

    public LinkService(int port) {
        this.port = port;
        beat.scheduleAtFixedRate(this::beatTick, BEAT_MS, BEAT_MS, TimeUnit.MILLISECONDS);
    }

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
    public LongProperty rttMsProperty() { return rttMs; }
    public IntegerProperty localBatteryProperty() { return localBattery; }
    public BooleanProperty localChargingProperty() { return localCharging; }
    public IntegerProperty peerBatteryProperty() { return peerBattery; }
    public BooleanProperty peerChargingProperty() { return peerCharging; }
    public StringProperty peerNetProperty() { return peerNet; }
    public ObservableList<Activity> activity() { return activity; }
    public Path inbox() { return inbox; }
    public boolean isConnected() { return connected.get(); }

    /** Adds a listener for messages from the peer. Listeners run on the FX thread. */
    public void addListener(Consumer<Msg> l) { listeners.add(l); }

    // ---- listening side ----

    public void startListening() {
        if (server != null && !server.isClosed()) return; // already listening
        Thread t = new Thread(() -> {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket(port);
                server = ss;
                fx(() -> listenError.set(""));
                while (!ss.isClosed()) handshakeIncoming(ss.accept());
            } catch (IOException e) {
                if (ss == null) {
                    String msg = "Không mở được cổng " + port + " vì đang bị ứng dụng khác dùng. "
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
        target = null;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        disconnect();
        sendQueue.shutdownNow();
        beat.shutdownNow();
    }

    private void handshakeIncoming(Socket s) {
        try {
            s.setSoTimeout(5000);
            tune(s);
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), BUF));
            DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), BUF));
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

    /**
     * Connect to the other device. host may be "ip" or "ip:port" (the port defaults to this app's port).
     * Ignored while a connection is already being made or active.
     */
    public void connectTo(String host, String code) {
        if (connected.get() || connecting.get()) return;
        String h = host.trim();
        int p = port;
        int colon = h.lastIndexOf(':');
        if (colon > 0) {
            try {
                p = Integer.parseInt(h.substring(colon + 1));
            } catch (NumberFormatException e) {
                error.set("Cổng không hợp lệ: " + h);
                return;
            }
            h = h.substring(0, colon);
        }
        startConnect(new Target(h, p, code.trim()), false);
    }

    private void startConnect(Target tg, boolean auto) {
        if (connected.get() || connecting.get()) return;
        connecting.set(true);
        if (!auto) error.set("");
        Thread t = new Thread(() -> connectOnce(tg, auto), "scft-connect");
        t.setDaemon(true);
        t.start();
    }

    private void connectOnce(Target tg, boolean auto) {
        Socket s = new Socket();
        String msg = null;
        boolean retry = false;
        try {
            s.connect(new InetSocketAddress(tg.host(), tg.port()), 5000);
            s.setSoTimeout(5000);
            tune(s);
            DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), BUF));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), BUF));
            o.writeUTF(MAGIC); o.writeUTF(tg.code()); o.writeUTF(myName);
            o.flush();
            String reply = in.readUTF(), name = in.readUTF();
            if (!reply.equals("OK")) {
                s.close();
                retry = auto && reply.equals("BUSY");
                msg = switch (reply) {
                    case "CODE" -> "Mã ghép nối chưa đúng. Kiểm tra lại mã trên thiết bị kia.";
                    case "BUSY" -> "Thiết bị kia đang kết nối với máy khác. Ngắt kết nối đó rồi thử lại.";
                    default -> "Hai máy đang dùng phiên bản SCFT không tương thích. Hãy cập nhật cả hai.";
                };
            } else {
                s.setSoTimeout(0);
                target = tg;
                retries = 0;
                adopt(s, in, o, name);
            }
        } catch (IOException e) {
            try { s.close(); } catch (IOException ignored) { }
            System.err.println("SCFT connect: " + e);
            retry = auto;
            msg = friendly(e, tg.host());
        } finally {
            String m = (auto && retry) ? null : msg; // automatic retries stay quiet until they give up
            fx(() -> { connecting.set(false); if (m != null) error.set(m); });
        }
        if (retry) scheduleReconnect(tg);
    }

    /** Vietnamese, user-facing wording for a socket failure. Technical detail goes to stderr. */
    private static String friendly(IOException e, String host) {
        if (e instanceof SocketTimeoutException)
            return "Hết thời gian chờ khi kết nối tới " + host + ". Kiểm tra hai máy cùng mạng "
                    + "và tường lửa có đang chặn cổng không.";
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

    /** After an unexpected drop, retry with 1, 2, 4 ... 30 s waits, up to MAX_RETRY times. */
    private void scheduleReconnect(Target tg) {
        if (target != tg) return; // user disconnected, or a newer link replaced this one
        if (retries >= MAX_RETRY) {
            target = null;
            fx(() -> statusText.set("Mất kết nối và không tự kết nối lại được. Bấm Kết nối để thử lại."));
            return;
        }
        int delay = Math.min(30, 1 << retries);
        retries++;
        int attempt = retries;
        fx(() -> statusText.set("Mất kết nối. Đang thử lại lần " + attempt + "/" + MAX_RETRY
                + " sau " + delay + " giây…"));
        beat.schedule(() -> {
            if (target == tg && !connected.get()) startConnect(tg, true);
        }, delay, TimeUnit.SECONDS);
    }

    // ---- shared ----

    private static void tune(Socket s) throws SocketException {
        s.setTcpNoDelay(true);
        s.setSendBufferSize(BUF);
        s.setReceiveBufferSize(BUF);
    }

    private void adopt(Socket s, DataInputStream in, DataOutputStream o, String name) {
        peer = s;
        out = o;
        lastRx = System.nanoTime();
        beats = 0;
        log("Kết nối", "•", "Đã kết nối với " + name);
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
                int type = in.readUnsignedByte();
                lastRx = System.nanoTime();
                if (type == Msg.T_BYE) { target = null; break; } // peer left on purpose: do not reconnect
                if (type == Msg.T_FILE) { receiveLegacyFile(in); continue; }
                handle(Msg.read(type, in));
            }
        } catch (IOException e) {
            if (!(e instanceof EOFException)) System.err.println("SCFT read: " + e);
        } finally {
            abortIncoming();
            if (peer == s) closePeer(false);
        }
    }

    private void handle(Msg m) throws IOException {
        switch (m) {
            case Msg.Ping p -> write(new Msg.Pong(p.stamp()));
            case Msg.Pong p -> {
                long rtt = (System.nanoTime() - p.stamp()) / 1_000_000L;
                fx(() -> rttMs.set(rtt));
            }
            case Msg.Status s -> fx(() -> {
                peerBattery.set(s.battery());
                peerCharging.set(s.charging());
                peerNet.set(s.net());
            });
            case Msg.FileStart f -> beginReceive(f);
            case Msg.FileData d -> receiveChunk(d.data());
            case Msg.FileEnd e -> endReceive(e.sha256());
            case Msg.FileAck a -> ackReceived(a.ok());
            default -> {
                logMsg(m, "←");
                dispatch(m);
            }
        }
    }

    private void dispatch(Msg m) {
        fx(() -> {
            for (Consumer<Msg> l : listeners) {
                try {
                    l.accept(m);
                } catch (RuntimeException e) {
                    System.err.println("SCFT listener: " + e);
                }
            }
        });
    }

    /** Writes one frame. Throws if there is no live link. */
    private void write(Msg m) throws IOException {
        DataOutputStream o = requireOut();
        synchronized (writeLock) {
            Msg.write(o, m);
            o.flush();
        }
    }

    private DataOutputStream requireOut() throws EOFException {
        DataOutputStream o = out;
        if (o == null) throw new EOFException("chưa kết nối thiết bị");
        return o;
    }

    /** Sends a control message (clipboard, chat, URL, ...). Returns false, with a reason, if it could not be sent. */
    public boolean sendControl(Msg m) {
        try {
            write(m);
            logMsg(m, "→");
            return true;
        } catch (IOException e) {
            String why = out == null ? "chưa kết nối thiết bị" : e.getMessage();
            fx(() -> error.set("Không gửi được: " + why));
            return false;
        }
    }

    private void logMsg(Msg m, String arrow) {
        switch (m) {
            case Msg.Clip c -> log("Clipboard", arrow, Activity.preview(c.text()));
            case Msg.Chat c -> log("Ghi chú", arrow, Activity.preview(c.text()));
            case Msg.Find f -> log("Tìm thiết bị", arrow, f.on() ? "Bật báo động" : "Tắt báo động");
            case Msg.Url u -> log("Mở URL", arrow, u.url());
            case Msg.ShotReq r -> log("Ảnh chụp", arrow, "Yêu cầu chụp màn hình");
            case Msg.ShotResp r -> log("Ảnh chụp", arrow, r.error() == null
                    ? "Đã chụp (" + TransferItem.human(r.png().length) + ")" : "Lỗi: " + r.error());
            case Msg.Media md -> log("Điều khiển media", arrow, SystemOps.mediaName(md.cmd()));
            case Msg.Notify n -> log("Thông báo", arrow, n.title() + ": " + n.body());
            case Msg.Reply r -> log("Phản hồi", arrow, (r.ok() ? "" : "Lỗi: ") + r.text());
            default -> { }
        }
    }

    /** Adds a line to the activity history. Safe from any thread. */
    void log(String kind, String arrow, String text) {
        Activity a = new Activity(LocalDateTime.now(), kind, arrow, text);
        fx(() -> {
            activity.add(0, a);
            while (activity.size() > LOG_MAX) activity.remove(activity.size() - 1);
        });
    }

    // ---- heartbeat ----

    private void beatTick() {
        try {
            SystemOps.Battery b = SystemOps.battery();
            fx(() -> {
                localBattery.set(b.percent());
                localCharging.set(b.charging());
            });
            if (out == null) return;
            if (System.nanoTime() - lastRx > DEAD_NS) {
                System.err.println("SCFT: no data from peer for 15 s, dropping link");
                closePeer(false);
                return;
            }
            write(new Msg.Ping(System.nanoTime()));
            if (++beats % 3 == 0) write(new Msg.Status(b.percent(), b.charging(), "LAN " + localAddress.get()));
        } catch (IOException | RuntimeException e) {
            System.err.println("SCFT beat: " + e);
        }
    }

    // ---- receiving files ----

    /** Opens the destination for a new incoming file. A write failure makes the file be discarded. */
    private void beginReceive(Msg.FileStart fs) throws IOException {
        if (incoming != null) throw new Msg.ProtocolException("Tệp mới bắt đầu khi tệp trước chưa xong");
        String base = fs.name().replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "_");
        if (base.isEmpty() || base.equals(".") || base.equals("..")) base = "file";
        TransferItem t = new TransferItem(base, fs.size(), Direction.PHONE_TO_PC);
        OutputStream os = null;
        Path dest = null;
        try {
            Files.createDirectories(inbox);
            dest = unique(inbox.resolve(base));
            os = Files.newOutputStream(dest);
        } catch (IOException e) {
            System.err.println("SCFT receive: " + e);
        }
        incoming = new Incoming(t, dest, os, fs.size());
        fx(() -> {
            transfers.add(0, t);
            t.active.set(true);
            t.status.set("Đang nhận");
        });
        if (os == null) abandon(incoming, "Không ghi được tệp vào " + inbox);
    }

    private void receiveChunk(byte[] data) throws Msg.ProtocolException {
        Incoming inc = incoming;
        if (inc == null) throw new Msg.ProtocolException("Dữ liệu tệp đến khi không có tệp đang nhận");
        if (inc.out != null) {
            try {
                inc.out.write(data);
            } catch (IOException e) {
                abandon(inc, "Ghi tệp thất bại: " + e.getMessage());
            }
        }
        inc.md.update(data);
        inc.got += data.length;
        if (inc.meter.tick(inc.got)) progress(inc.item, inc.got, inc.size, inc.meter.bps());
    }

    /** Verifies SHA-256, tells the sender the verdict, and keeps or deletes the file. */
    private void endReceive(byte[] sha) throws IOException {
        Incoming inc = incoming;
        if (inc == null) throw new Msg.ProtocolException("Kết thúc tệp khi không có tệp đang nhận");
        incoming = null;
        byte[] mine = inc.md.digest();
        boolean ok = inc.out != null && Arrays.equals(mine, sha);
        if (inc.out != null) {
            try { inc.out.close(); } catch (IOException e) { ok = false; }
        }
        write(new Msg.FileAck(ok));
        TransferItem t = inc.item;
        if (ok) {
            Path saved = inc.dest;
            String hex = hex(mine);
            long size = inc.size;
            fx(() -> {
                t.progress.set(1);
                t.done.set(size);
                t.speed.set(0);
                t.active.set(false);
                t.savedTo.set(saved);
                t.sha256.set(hex);
                t.status.set("Đã lưu · SHA-256 khớp: " + saved.getFileName());
            });
            log("Tệp", "←", "Nhận " + saved.getFileName() + " (" + TransferItem.human(size) + ")");
        } else {
            abandon(inc, "SHA-256 không khớp: tệp bị hỏng khi truyền, đã xóa");
        }
    }

    private void abortIncoming() {
        Incoming inc = incoming;
        incoming = null;
        if (inc != null) abandon(inc, "Nhận thất bại: kết nối bị gián đoạn");
    }

    /** Closes and deletes a partial file, and marks the item as failed. */
    private void abandon(Incoming inc, String msg) {
        if (inc.out != null) {
            try { inc.out.close(); } catch (IOException ignored) { }
            inc.out = null;
        }
        if (inc.dest != null) {
            try { Files.deleteIfExists(inc.dest); } catch (IOException ignored) { }
        }
        fail(inc.item, msg);
    }

    /** The sender's verdict for the oldest file it is waiting on. */
    private void ackReceived(boolean ok) {
        TransferItem t = awaitingAck.poll();
        if (t == null) return;
        fx(() -> t.status.set(ok ? "Hoàn tất · máy kia đã xác minh SHA-256"
                : "Máy kia báo SHA-256 không khớp: hãy gửi lại"));
    }

    /** Old peers send a whole file in one frame (type 1). Kept so they can still send to us. */
    private void receiveLegacyFile(DataInputStream in) throws IOException {
        String raw = in.readUTF();
        long size = in.readLong();
        Path fn = Paths.get(raw).getFileName(); // never trust the sender's path
        String name = fn == null ? "file" : fn.toString();
        TransferItem t = new TransferItem(name, size, Direction.PHONE_TO_PC);
        fx(() -> { transfers.add(0, t); t.active.set(true); t.status.set("Đang nhận"); });
        Files.createDirectories(inbox);
        Path dest = unique(inbox.resolve(name));
        try (OutputStream f = Files.newOutputStream(dest)) {
            byte[] buf = new byte[BUF];
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
            try { Files.deleteIfExists(dest); } catch (IOException ignored) { }
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

    // ---- sending files ----

    /** Queue a file for sending. Files are sent one after another; the rest show "Đang chờ". */
    public void send(File f) {
        if (!connected.get() || out == null) {
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
        sendQueue.execute(() -> doSend(t, f));
    }

    /**
     * Sends a file as FILE_START, then data frames of at most 256 KB, then FILE_END with the SHA-256.
     * The write lock is taken per frame, so heartbeats and chat still get through during a big transfer.
     */
    private void doSend(TransferItem t, File f) {
        if (t.cancelled) return;
        fx(() -> t.status.set("Đang gửi"));
        MessageDigest md = sha256();
        long size = f.length();
        try (InputStream in = new FileInputStream(f)) {
            write(new Msg.FileStart(f.getName(), size));
            byte[] buf = new byte[Msg.MAX_CHUNK];
            Meter meter = new Meter();
            long sent = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                if (t.cancelled) return; // link was closed; the socket is being dropped anyway
                md.update(buf, 0, n);
                DataOutputStream o = requireOut();
                synchronized (writeLock) {
                    Msg.writeData(o, buf, n);
                    o.flush();
                }
                sent += n;
                if (meter.tick(sent)) progress(t, sent, size, meter.bps());
            }
            byte[] sum = md.digest();
            String hex = hex(sum);
            // Status is queued before FILE_END goes out, so the verdict can never overtake it.
            fx(() -> t.status.set("Đã gửi, chờ máy kia kiểm tra"));
            awaitingAck.add(t);
            try {
                write(new Msg.FileEnd(sum));
            } catch (IOException e) {
                awaitingAck.remove(t);
                throw e;
            }
            fx(() -> {
                t.progress.set(1);
                t.done.set(size);
                t.speed.set(0);
                t.active.set(false);
                t.sha256.set(hex);
            });
            log("Tệp", "→", "Gửi " + f.getName() + " (" + TransferItem.human(size) + ")");
        } catch (IOException e) {
            awaitingAck.remove(t);
            if (t.cancelled) return;
            System.err.println("SCFT send: " + e);
            fail(t, "Gửi thất bại: " + (e instanceof FileNotFoundException
                    ? "không đọc được tệp" : "kết nối bị gián đoạn"));
            if (!(e instanceof FileNotFoundException)) closePeer(false);
        }
    }

    private void progress(TransferItem t, long done, long total, double bps) {
        fx(() -> {
            t.done.set(done);
            t.progress.set(total == 0 ? 1 : Math.min(1, done / (double) total));
            t.speed.set(bps);
        });
    }

    /** Mark an active transfer as failed with a Vietnamese message (no-op if already finished). */
    private void fail(TransferItem t, String msg) {
        fx(() -> {
            if (t.active.get()) {
                t.active.set(false);
                t.speed.set(0);
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

    // ---- ending the link ----

    public void disconnect() {
        target = null; // a user disconnect never reconnects
        DataOutputStream o = out;
        if (o != null) {
            try { synchronized (writeLock) { o.writeByte(Msg.T_BYE); o.flush(); } } catch (IOException ignored) { }
        }
        closePeer(true);
    }

    /**
     * Drops the socket and updates the UI. newCode regenerates the pairing code, which a user disconnect
     * does; an unexpected drop keeps it so that a reconnect from the other side still works.
     */
    private synchronized void closePeer(boolean regenerateCode) {
        Socket s = peer;
        if (s == null) return;
        peer = null;
        out = null;
        try { s.close(); } catch (IOException ignored) { }
        Target tg = target;
        fx(() -> {
            for (TransferItem t : transfers) {
                if (t.active.get()) {
                    t.cancelled = true;
                    t.active.set(false);
                    t.status.set("Đã hủy do ngắt kết nối");
                }
            }
            TransferItem waiting;
            while ((waiting = awaitingAck.poll()) != null) {
                waiting.status.set("Đã gửi, chưa xác minh (mất kết nối)");
            }
            mirroring.set(false);
            connected.set(false);
            deviceName.set("—");
            statusText.set("Chưa kết nối thiết bị");
            rttMs.set(-1);
            peerBattery.set(-1);
            peerNet.set("");
            if (regenerateCode) pairingCode.set(newCode());
        });
        if (tg != null) scheduleReconnect(tg);
    }

    /** Test hook: drop the socket as if the network failed. */
    void dropLinkForTest() {
        closePeer(false);
    }

    // ---- simulated mirror (unchanged) ----

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

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
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
