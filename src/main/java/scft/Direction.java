package scft;

public enum Direction {
    PHONE_TO_PC("Điện thoại → Máy tính"),
    PC_TO_PHONE("Máy tính → Điện thoại");

    private final String label;
    Direction(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
