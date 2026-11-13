package scft;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

/**
 * Everything that touches this PC's operating system: media keys, volume, browser, notifications,
 * battery and screenshots. Linux tools are called without a shell. A missing tool is reported as
 * "not available" instead of an exception, so the rest of the app keeps working.
 */
final class SystemOps {
    static final int MEDIA_PLAY_PAUSE = 0, MEDIA_NEXT = 1, MEDIA_PREV = 2,
            MEDIA_VOL_UP = 3, MEDIA_VOL_DOWN = 4, MEDIA_MUTE = 5;
    private static final String[] MEDIA_NAMES = {
            "Phát/tạm dừng", "Bài kế tiếp", "Bài trước", "Tăng âm lượng", "Giảm âm lượng", "Tắt/bật tiếng"};

    /** percent is -1 when there is no battery or it cannot be read. */
    record Battery(int percent, boolean charging) {}

    private SystemOps() { }

    static String mediaName(int cmd) {
        return cmd >= 0 && cmd < MEDIA_NAMES.length ? MEDIA_NAMES[cmd] : "Lệnh " + cmd;
    }

    static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    /** True when an executable with this name is on PATH. */
    static boolean has(String command) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(File.pathSeparator)) {
            try {
                if (Files.isExecutable(Paths.get(dir, command))) return true;
            } catch (RuntimeException ignored) { }
        }
        return false;
    }

    /** Runs a command without a shell. Returns the exit code, or -1 if it could not start or timed out. */
    static int run(String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); return -1; }
            return p.exitValue();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** Runs a media key on this PC. Returns null on success, otherwise the reason it did not work. */
    static String media(int cmd) {
        if (!isLinux()) return "Điều khiển media mới hỗ trợ Linux trong bản này.";
        switch (cmd) {
            case MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREV -> {
                if (!has("playerctl")) return "Không khả dụng: máy này chưa cài playerctl (sudo apt install playerctl).";
                String verb = cmd == MEDIA_PLAY_PAUSE ? "play-pause" : cmd == MEDIA_NEXT ? "next" : "previous";
                return run("playerctl", verb) == 0 ? null : "Không có trình phát media nào đang chạy.";
            }
            case MEDIA_VOL_UP, MEDIA_VOL_DOWN, MEDIA_MUTE -> {
                if (has("wpctl")) {
                    int rc = cmd == MEDIA_MUTE
                            ? run("wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "toggle")
                            : run("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", cmd == MEDIA_VOL_UP ? "5%+" : "5%-");
                    if (rc == 0) return null;
                }
                if (has("amixer")) {
                    int rc = cmd == MEDIA_MUTE
                            ? run("amixer", "-q", "sset", "Master", "toggle")
                            : run("amixer", "-q", "sset", "Master", cmd == MEDIA_VOL_UP ? "5%+" : "5%-");
                    if (rc == 0) return null;
                }
                return "Không khả dụng: cần wpctl (PipeWire) hoặc amixer (ALSA).";
            }
            default -> {
                return "Lệnh media không rõ: " + cmd;
            }
        }
    }

    /** Parses a link that may be opened. Only http and https are allowed. */
    static URI webUrl(String text) {
        String t = text == null ? "" : text.trim();
        try {
            URI u = new URI(t);
            String scheme = u.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && u.getHost() != null) return u;
        } catch (URISyntaxException ignored) { }
        throw new IllegalArgumentException("Chỉ mở được liên kết bắt đầu bằng http:// hoặc https://");
    }

    /** Opens a web link in this PC's browser. */
    static void openUrl(String text) throws IOException {
        URI u = webUrl(text);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(u);
                return;
            }
        } catch (RuntimeException | IOException ignored) {
            // fall through to xdg-open
        }
        if (isLinux() && run("xdg-open", u.toString()) == 0) return;
        throw new IOException("Không mở được trình duyệt (cần xdg-open).");
    }

    /** Shows a desktop notification with notify-send. Returns false when it is not available. */
    static boolean notifyDesktop(String title, String body) {
        if (!isLinux() || !has("notify-send")) return false;
        return run("notify-send", "-a", "SCFT", title, body) == 0;
    }

    /** Battery state from sysfs (Linux). Desktops without a battery report -1. */
    static Battery battery() {
        File[] devices = new File("/sys/class/power_supply").listFiles();
        if (devices == null) return new Battery(-1, false);
        for (File d : devices) {
            try {
                if (!"Battery".equals(read(new File(d, "type")))) continue;
                int percent = Integer.parseInt(read(new File(d, "capacity")));
                return new Battery(percent, "Charging".equals(read(new File(d, "status"))));
            } catch (IOException | NumberFormatException ignored) {
                // try the next supply
            }
        }
        return new Battery(-1, false);
    }

    private static String read(File f) throws IOException {
        return Files.readString(f.toPath()).trim();
    }

    /**
     * Captures every screen as one PNG. Tries the tools that exist on this system, in order:
     * grim (wlroots Wayland, PNG on stdout), gnome-screenshot and spectacle (write a file), ImageMagick
     * import (X11), and AWT Robot (X11 only: on Wayland it returns XWayland content or hangs).
     * Throws IOException with the reason when nothing works.
     */
    static byte[] capturePng() throws IOException {
        StringBuilder why = new StringBuilder();
        boolean wayland = "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
        if (isLinux()) {
            if (has("grim")) {
                byte[] b = capture("grim", "-");
                if (b != null) return b;
            }
            if (has("gnome-screenshot")) {
                byte[] b = captureToFile("gnome-screenshot", "-f");
                if (b != null) return b;
            }
            if (has("spectacle")) {
                byte[] b = captureToFile("spectacle", "-b", "-n", "-f", "-o");
                if (b != null) return b;
            }
            if (!wayland && has("import")) {
                byte[] b = capture("import", "-window", "root", "png:-");
                if (b != null) return b;
            }
        }
        if (!wayland && !GraphicsEnvironment.isHeadless()) {
            try {
                return robotCapture();
            } catch (IOException e) {
                why.append(" (").append(e.getMessage()).append(")");
            }
        }
        throw new IOException("Không chụp được màn hình" + why + ". Trên Wayland cài grim hoặc gnome-screenshot; "
                + "trên X11 cài imagemagick (import).");
    }

    /** AWT Robot capture of all screens, with a time limit so a stuck capture cannot block forever. */
    private static byte[] robotCapture() throws IOException {
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "scft-robot");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<byte[]> f = ex.submit(() -> {
                Rectangle all = null;
                for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                    Rectangle b = d.getDefaultConfiguration().getBounds();
                    all = all == null ? b : all.union(b);
                }
                if (all == null) throw new IOException("không tìm thấy màn hình");
                BufferedImage img = new Robot().createScreenCapture(all);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(img, "png", bo);
                return bo.toByteArray();
            });
            return f.get(8, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("AWT Robot không trả lời trong 8 giây");
        } catch (ExecutionException e) {
            throw new IOException(String.valueOf(e.getCause()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("bị ngắt");
        } finally {
            ex.shutdownNow();
        }
    }

    /** Runs a command that writes a PNG to stdout. Returns null if it failed. */
    private static byte[] capture(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] data = p.getInputStream().readAllBytes();
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0 || data.length == 0) return null;
            return data;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Runs a screenshot tool that writes its PNG to the path given last. Returns null if it failed. */
    private static byte[] captureToFile(String... commandWithFlags) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("scft-shot-", ".png");
            String[] cmd = Arrays.copyOf(commandWithFlags, commandWithFlags.length + 1);
            cmd[cmd.length - 1] = tmp.toString();
            if (run(cmd) != 0) return null;
            byte[] data = Files.readAllBytes(tmp);
            return data.length == 0 ? null : data;
        } catch (IOException e) {
            return null;
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
        }
    }
}
