package scft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Self-test without JUnit (no JUnit jars are cached offline). Covers the frame codec and a real
 * loopback link between two LinkService instances on 127.0.0.1. Run it with the compiled classes
 * and the JavaFX jars on the classpath; see the notes in the report. It writes its files under
 * user.home, so pass -Duser.home=<scratch dir> to keep the inbox out of the real Downloads folder.
 */
public final class LinkServiceSelfTest {
    private static int checks, failures;

    public static void main(String[] args) throws Exception {
        testCodec();
        testUnknownFrame();
        testLoopback();
        System.out.println(checks + " checks, " + failures + " failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ---- codec ----

    private static void testCodec() throws IOException {
        Msg[] samples = {
                new Msg.Ping(123456789L),
                new Msg.Pong(42L),
                new Msg.Status(80, true, "LAN 192.168.1.5"),
                new Msg.Status(-1, false, ""),
                new Msg.Clip("Xin chào, tiếng Việt: ăâđêôơư"),
                new Msg.Chat("ghi chú nhanh"),
                new Msg.Find(true),
                new Msg.Url("https://example.com/?q=ă&x=1"),
                new Msg.ShotReq(99L),
                new Msg.ShotResp(99L, null, new byte[]{1, 2, 3, (byte) 0xFF}),
                new Msg.ShotResp(99L, "Người dùng từ chối", null),
                new Msg.Media(5),
                new Msg.Notify("Tiêu đề", "nội dung"),
                new Msg.Reply(Msg.T_MEDIA, false, "không có playerctl"),
                new Msg.FileStart("a b.txt", 1234L),
                new Msg.FileData(new byte[]{9, 8, 7}),
                new Msg.FileEnd(new byte[32]),
                new Msg.FileAck(true),
        };
        for (Msg m : samples) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (DataOutputStream o = new DataOutputStream(bo)) {
                Msg.write(o, m);
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bo.toByteArray()));
            Msg back = Msg.read(in.readUnsignedByte(), in);
            check(same(m, back) && in.available() == 0, "codec round-trip " + m.getClass().getSimpleName());
        }
        // Clip text larger than the 1 MB limit must be refused, not sent half-written.
        boolean refused = false;
        try {
            Msg.write(new DataOutputStream(new ByteArrayOutputStream()),
                    new Msg.Clip("x".repeat(Msg.MAX_TEXT + 1)));
        } catch (IOException e) {
            refused = true;
        }
        check(refused, "oversized clipboard text is refused");
    }

    private static void testUnknownFrame() {
        boolean rejected = false;
        try {
            Msg.read(200, new DataInputStream(new ByteArrayInputStream(new byte[0])));
        } catch (Msg.ProtocolException e) {
            rejected = true;
        } catch (IOException e) {
            rejected = false;
        }
        check(rejected, "unknown frame type is a protocol error");
        check(Msg.T_FILE == 1 && Msg.T_BYE == 2, "legacy frame numbers T_FILE=1 and T_BYE=2 are unchanged");
    }

    // ---- live loopback ----

    private static void testLoopback() throws Exception {
        int portA = 45601, portB = 45602;
        LinkService a = new LinkService(portA);
        LinkService b = new LinkService(portB);
        List<Msg> atA = new CopyOnWriteArrayList<>();
        a.addListener(atA::add);
        a.startListening();
        Thread.sleep(300);

        b.connectTo("127.0.0.1:" + portA, a.pairingCodeProperty().get());
        check(waitFor(() -> a.isConnected() && b.isConnected(), 5000), "two instances connect over loopback");
        String codeBefore = a.pairingCodeProperty().get();

        b.sendControl(new Msg.Clip("đồng bộ clipboard"));
        check(waitFor(() -> atA.stream().anyMatch(m -> m instanceof Msg.Clip c && c.text().equals("đồng bộ clipboard")), 3000),
                "clipboard text arrives at the other instance");

        check(waitFor(() -> b.rttMsProperty().get() >= 0, 6000), "ping/pong measures RTT (" + b.rttMsProperty().get() + " ms)");
        check(waitFor(() -> !b.peerNetProperty().get().isEmpty(), 8000), "status heartbeat (network) reaches the other side");

        // File with SHA-256: 1.5 MB is six data frames.
        Path inboxA = a.inbox();
        Path src = Paths.get(System.getProperty("java.io.tmpdir"), "scft-selftest-" + System.nanoTime() + ".bin");
        byte[] data = new byte[1536 * 1024];
        new SecureRandom().nextBytes(data);
        Files.write(src, data);
        String name = src.getFileName().toString();
        Path received = inboxA.resolve(name);
        Files.deleteIfExists(received);
        b.send(src.toFile());
        check(waitFor(() -> Files.exists(received) && fileSize(received) == data.length, 10000),
                "file arrives at the other instance");
        check(waitFor(() -> b.transfers().stream().anyMatch(t -> t.name.equals(name)
                        && t.status.get().contains("xác minh")), 5000),
                "sender gets the SHA-256 verdict from the receiver");
        check(Files.exists(received) && Arrays.equals(Files.readAllBytes(received), data),
                "received bytes are identical to the source");
        check(waitFor(() -> a.transfers().stream().anyMatch(t -> t.name.equals(name) && !t.sha256.get().isEmpty()), 2000),
                "receiver records the SHA-256 on the transfer row");

        // Unexpected drop: the connecting side reconnects with the same code.
        b.dropLinkForTest();
        check(waitFor(() -> a.isConnected() && b.isConnected(), 8000), "connecting side reconnects after a drop");
        check(a.pairingCodeProperty().get().equals(codeBefore), "pairing code survives an unexpected drop");

        // User disconnect: the other side ends, and nobody reconnects.
        b.disconnect();
        check(waitFor(() -> !a.isConnected(), 3000), "peer sees a user disconnect");
        Thread.sleep(3000);
        check(!b.isConnected() && !a.isConnected(), "no automatic reconnect after a user disconnect");

        a.stop();
        b.stop();
        Files.deleteIfExists(src);
        Files.deleteIfExists(received);
    }

    // ---- helpers ----

    private static long fileSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }

    private static boolean waitFor(BooleanSupplier cond, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) return true;
            Thread.sleep(50);
        }
        return cond.getAsBoolean();
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) failures++;
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
    }

    private static boolean same(Msg a, Msg b) {
        if (a instanceof Msg.FileData x && b instanceof Msg.FileData y) return Arrays.equals(x.data(), y.data());
        if (a instanceof Msg.FileEnd x && b instanceof Msg.FileEnd y) return Arrays.equals(x.sha256(), y.sha256());
        if (a instanceof Msg.ShotResp x && b instanceof Msg.ShotResp y)
            return x.id() == y.id() && Objects.equals(x.error(), y.error()) && Arrays.equals(x.png(), y.png());
        return a.equals(b);
    }
}
