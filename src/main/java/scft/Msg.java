package scft;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Frames on the LAN socket. Every frame is one type byte followed by its fields.
 * T_FILE (1) and T_BYE (2) keep their original meaning. The other types were added with the
 * utilities; an unknown type byte is a protocol error and the link is dropped.
 */
public sealed interface Msg {
    byte T_FILE = 1;          // legacy whole-file frame, still read for older peers
    byte T_BYE = 2;
    byte T_PING = 3, T_PONG = 4, T_STATUS = 5;
    byte T_CLIP = 6, T_CHAT = 7, T_FIND = 8, T_URL = 9;
    byte T_SHOT_REQ = 10, T_SHOT_RESP = 11, T_MEDIA = 12, T_NOTIFY = 13, T_REPLY = 14;
    byte T_FILE_START = 15, T_FILE_DATA = 16, T_FILE_END = 17, T_FILE_ACK = 18;

    int MAX_TEXT = 1 << 20;            // 1 MB per text field
    int MAX_CHUNK = 256 * 1024;        // one file data frame
    int MAX_IMAGE = 32 << 20;          // screenshot payload

    record Ping(long stamp) implements Msg {}
    record Pong(long stamp) implements Msg {}
    /** battery is -1 when the device has none or it cannot be read. */
    record Status(int battery, boolean charging, String net) implements Msg {}
    record Clip(String text) implements Msg {}
    record Chat(String text) implements Msg {}
    record Find(boolean on) implements Msg {}
    record Url(String url) implements Msg {}
    record ShotReq(long id) implements Msg {}
    /** error != null means the capture failed; otherwise png holds the image. */
    record ShotResp(long id, String error, byte[] png) implements Msg {}
    /** cmd is one of SystemOps.MEDIA_*. */
    record Media(int cmd) implements Msg {}
    record Notify(String title, String body) implements Msg {}
    /** Answer to a request; about is the type byte of that request. */
    record Reply(int about, boolean ok, String text) implements Msg {}
    record FileStart(String name, long size) implements Msg {}
    record FileData(byte[] data) implements Msg {}
    record FileEnd(byte[] sha256) implements Msg {}
    record FileAck(boolean ok) implements Msg {}

    /** The stream is out of sync or the peer sent something this build does not understand. */
    final class ProtocolException extends IOException {
        public ProtocolException(String message) { super(message); }
    }

    static void write(DataOutputStream o, Msg m) throws IOException {
        switch (m) {
            case Ping p -> { o.writeByte(T_PING); o.writeLong(p.stamp()); }
            case Pong p -> { o.writeByte(T_PONG); o.writeLong(p.stamp()); }
            case Status s -> { o.writeByte(T_STATUS); o.writeInt(s.battery()); o.writeBoolean(s.charging()); text(o, s.net()); }
            case Clip c -> { o.writeByte(T_CLIP); text(o, c.text()); }
            case Chat c -> { o.writeByte(T_CHAT); text(o, c.text()); }
            case Find f -> { o.writeByte(T_FIND); o.writeBoolean(f.on()); }
            case Url u -> { o.writeByte(T_URL); text(o, u.url()); }
            case ShotReq r -> { o.writeByte(T_SHOT_REQ); o.writeLong(r.id()); }
            case ShotResp r -> {
                o.writeByte(T_SHOT_RESP);
                o.writeLong(r.id());
                if (r.error() != null) { o.writeBoolean(false); text(o, r.error()); }
                else { o.writeBoolean(true); bytes(o, r.png(), MAX_IMAGE); }
            }
            case Media md -> { o.writeByte(T_MEDIA); o.writeByte(md.cmd()); }
            case Notify n -> { o.writeByte(T_NOTIFY); text(o, n.title()); text(o, n.body()); }
            case Reply r -> { o.writeByte(T_REPLY); o.writeByte(r.about()); o.writeBoolean(r.ok()); text(o, r.text()); }
            case FileStart f -> { o.writeByte(T_FILE_START); text(o, f.name()); o.writeLong(f.size()); }
            case FileData d -> writeData(o, d.data(), d.data().length);
            case FileEnd e -> { o.writeByte(T_FILE_END); bytes(o, e.sha256(), 64); }
            case FileAck a -> { o.writeByte(T_FILE_ACK); o.writeBoolean(a.ok()); }
        }
    }

    /** Writes one data frame from a reusable buffer, without copying it into a record first. */
    static void writeData(DataOutputStream o, byte[] buf, int len) throws IOException {
        o.writeByte(T_FILE_DATA);
        o.writeInt(len);
        o.write(buf, 0, len);
    }

    /** Reads the body of a frame whose type byte has already been read. */
    static Msg read(int type, DataInputStream in) throws IOException {
        return switch (type) {
            case T_PING -> new Ping(in.readLong());
            case T_PONG -> new Pong(in.readLong());
            case T_STATUS -> new Status(in.readInt(), in.readBoolean(), readText(in));
            case T_CLIP -> new Clip(readText(in));
            case T_CHAT -> new Chat(readText(in));
            case T_FIND -> new Find(in.readBoolean());
            case T_URL -> new Url(readText(in));
            case T_SHOT_REQ -> new ShotReq(in.readLong());
            case T_SHOT_RESP -> {
                long id = in.readLong();
                yield in.readBoolean()
                        ? new ShotResp(id, null, readBytes(in, MAX_IMAGE))
                        : new ShotResp(id, readText(in), null);
            }
            case T_MEDIA -> new Media(in.readUnsignedByte());
            case T_NOTIFY -> new Notify(readText(in), readText(in));
            case T_REPLY -> new Reply(in.readUnsignedByte(), in.readBoolean(), readText(in));
            case T_FILE_START -> new FileStart(readText(in), in.readLong());
            case T_FILE_DATA -> new FileData(readBytes(in, MAX_CHUNK));
            case T_FILE_END -> new FileEnd(readBytes(in, 64));
            case T_FILE_ACK -> new FileAck(in.readBoolean());
            default -> throw new ProtocolException("Khung không rõ: " + type);
        };
    }

    private static void text(DataOutputStream o, String s) throws IOException {
        bytes(o, s.getBytes(UTF_8), MAX_TEXT);
    }

    private static void bytes(DataOutputStream o, byte[] b, int max) throws IOException {
        if (b.length > max) throw new IOException("Nội dung quá lớn: " + b.length + " byte");
        o.writeInt(b.length);
        o.write(b);
    }

    private static String readText(DataInputStream in) throws IOException {
        return new String(readBytes(in, MAX_TEXT), UTF_8);
    }

    private static byte[] readBytes(DataInputStream in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new ProtocolException("Khung quá lớn: " + n + " byte");
        byte[] b = new byte[n];
        in.readFully(b);
        return b;
    }
}
