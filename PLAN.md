# SCFT – Plan

Mục tiêu: app JavaFX PC <-> điện thoại (Flip3, Tailscale 100.115.117.61) ghép nối bằng mã, truyền dữ liệu hai chiều.

## Giai đoạn
1. **Front end**: agent phê bình UI/UX (CRITIQUE.md) -> agent sửa (theo CRITIQUE.md). Lặp tới khi hết lỗi P0/P1.
2. **Chức năng mới** (>= 10 chức năng hai chiều, thêm vào giao thức LinkService, mỗi chức năng một tab/thẻ):
   1. Clipboard sync (văn bản, hai chiều)
   2. Gửi tin nhắn/ghi chú nhanh (chat nhỏ)
   3. Thông báo điện thoại -> PC (mirror notification)
   4. Tìm điện thoại (đổ chuông / báo "tìm PC")
   5. Trạng thái pin & mạng hai chiều (heartbeat + ping/RTT)
   6. Mở URL trên thiết bị kia
   7. Chụp ảnh màn hình thiết bị kia (screenshot request)
   8. Điều khiển media (play/pause/vol) từ PC sang phone và ngược lại
   9. Gửi nhiều tệp/thư mục, kéo-thả, tiếp tục khi đứt (resume) + checksum
   10. Lịch sử truyền + tìm kiếm + mở thư mục nhận
   11. Chia sẻ danh bạ/SMS gửi từ PC (tuỳ chọn, cần quyền)
   12. Đồng bộ ảnh mới từ camera phone
3. **Tối ưu**: buffer 256KB, tính tốc độ/ETA, nén tuỳ chọn, tự kết nối lại, lưu thiết bị đã ghép.
4. **Demo**: scrcpy qua Tailscale tới Flip3 (100.115.117.61:5555) – tuân thủ ràng buộc từ phiên flip-camera: dùng `adb connect` đúng target, KHÔNG reset ADB, không bật camera/ghi hình, không phá TLS/pairing.

## Phân vai agent (Haiku 5.5)
- Critic: chỉ đọc, ghi CRITIQUE.md.
- Fixer: sửa UI theo CRITIQUE.md.
- Demo agent (sau khi front end xong): chạy app + scrcpy + quay demo.
