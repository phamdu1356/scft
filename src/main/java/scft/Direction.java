package scft;

public enum Direction {
    PHONE_TO_PC("Điện thoại → Máy tính", "Điện thoại → PC"),
    PC_TO_PHONE("Máy tính → Điện thoại", "PC → Điện thoại");

    private final String label;
    private final String shortLabel;
    Direction(String label, String shortLabel) { this.label = label; this.shortLabel = shortLabel; }
    public String shortLabel() { return shortLabel; }
    @Override public String toString() { return label; }
}
