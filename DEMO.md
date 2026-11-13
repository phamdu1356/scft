# SCFT – Hướng dẫn demo

Tài liệu này mô tả cách chạy SCFT, các bước demo 10 chức năng ở tab **Tiện ích** và **Tệp & dữ liệu**, kết quả thực tế đã chạy được trên một máy Linux, và các hạn chế còn lại.

## 1. Cách chạy

Yêu cầu: JDK 21, Maven (build offline dùng kho `~/.m2` đã có JavaFX 21.0.5, atlantafx 2.0.1, ikonli 12.3.1).

```bash
export JAVA_HOME=<đường dẫn JDK 21>
cd /home/pham-du/code/SCFT
mvn -q compile
```

Chạy app (lệnh `java` trực tiếp, tương đương `mvn javafx:run`; mục đích là truyền được `-Dscft.port` và `-Duser.home` riêng cho từng instance):

```bash
# Lấy classpath: jar JavaFX linux đặt ở --module-path, còn lại đặt ở -cp
CP=$(mvn -o -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)
JFX=$(echo "$CP" | tr ':' '\n' | grep openjfx | grep linux | paste -sd:)
REST=$(echo "$CP" | tr ':' '\n' | grep -v openjfx | paste -sd:)

# Instance A (máy chờ kết nối, cổng 45461, thư mục riêng)
java --module-path "$JFX" --add-modules javafx.controls \
     -Dscft.port=45461 -Duser.home=/đường/dẫn/A \
     -cp "target/classes:$REST" scft.Main

# Instance B (cổng 45462, thư mục riêng)
java --module-path "$JFX" --add-modules javafx.controls \
     -Dscft.port=45462 -Duser.home=/đường/dẫn/B \
     -cp "target/classes:$REST" scft.Main
```

- `-Dscft.port` cho phép hai instance trên cùng máy. Mặc định là 45454.
- `-Duser.home` đổi thư mục nhận tệp (`<user.home>/Downloads/SCFT`) và nơi lưu tùy chọn, để demo không ghi vào thư mục thật.
- Trên hai máy thật: không cần `-Dscft.port`. Máy B nhập địa chỉ IP của máy A và mã ghép nối hiện ở tab Thiết bị.

Kiểm tra giao thức không cần GUI:

```bash
javac -d <thư mục tạm> -cp "target/classes:$REST" src/test/java/scft/LinkServiceSelfTest.java
java -Duser.home=<thư mục tạm> -cp "<thư mục tạm>:target/classes:$REST" scft.LinkServiceSelfTest
```

Kết quả: **33 kiểm tra, 0 lỗi** (codec, khung không rõ, kết nối loopback, ping, trạng thái, truyền tệp với SHA-256, tự kết nối lại, không tự kết nối lại sau khi người dùng ngắt).

## 2. Hướng dẫn demo từng bước

Chuẩn bị: mở A và B. Trên A, vào tab **Thiết bị** và ghi lại địa chỉ + mã ghép nối. Trên B, nhập địa chỉ đó (ví dụ `127.0.0.1:45461` nếu cùng máy) và mã, rồi bấm **Kết nối**. Thanh trạng thái dưới cùng phải hiện "Đã kết nối". Các nút ở tab Tiện ích chỉ bật khi đã kết nối.

Trên A, bật ô **Tự đồng bộ văn bản clipboard (hai chiều)** trước khi demo mục 1.

### 1. Trạng thái & ping
- Vị trí: Tiện ích, thẻ "Trạng thái & ping".
- Thao tác: không cần, số liệu tự cập nhật (ping mỗi 2 giây, pin và mạng mỗi 6 giây).
- Kỳ vọng: "Ping (RTT): X ms", "Pin máy này", "Pin máy kia", "Mạng máy kia: LAN <IP>". Máy không có pin hiện "không rõ".

### 2. Đồng bộ clipboard (hai chiều)
- Vị trí: thẻ "Đồng bộ clipboard".
- Thao tác: B copy một đoạn văn bản, bấm **Gửi clipboard máy này ngay**. Chiều ngược lại: bật ô tự đồng bộ trên cả hai máy, copy ở A.
- Kỳ vọng: nhãn bên dưới ghi "Đã gửi: …" ở B và "Đã nhận và chép vào clipboard: …" ở A. Clipboard của A chứa đúng văn bản.

### 3. Chat & ghi chú nhanh
- Vị trí: thẻ "Chat & ghi chú nhanh".
- Thao tác: B gõ "Xin chào" rồi Enter (hoặc bấm **Gửi**). A trả lời tương tự.
- Kỳ vọng: tin nhắn hiện ở cả hai bên, có giờ, nhãn "Tôi" hoặc "Máy kia".

### 4. Tìm thiết bị
- Vị trí: thẻ "Tìm thiết bị".
- Thao tác: B bấm **Báo máy kia (bíp và rung)**, rồi **Dừng báo** sau vài giây.
- Kỳ vọng: A hiện "Máy kia đang tìm thiết bị này (bíp và rung)", cửa sổ A rung và phát tiếng bíp trong 10 giây. Bấm **Dừng báo** thì nhãn ở A biến mất.

### 5. Mở liên kết trên máy kia
- Vị trí: thẻ "Mở liên kết trên máy kia".
- Thao tác: B nhập `https://example.com/`, bấm **Mở trên máy kia**.
- Kỳ vọng: trình duyệt của A mở liên kết. B nhận "Máy kia: Đã mở liên kết trên máy này." Chỉ nhận http và https.

### 6. Chụp màn hình máy kia
- Vị trí: thẻ "Chụp màn hình máy kia".
- Thao tác: B bấm **Yêu cầu chụp màn hình máy kia**. Trên A, hộp thoại hỏi có cho phép. Thử lần 1 bấm **Hủy**, lần 2 bấm **OK**.
- Kỳ vọng: bấm Hủy thì B nhận "Người dùng từ chối". Bấm OK thì ảnh được gửi về và lưu vào thư mục nhận. Trên máy không có công cụ chụp, B nhận thông báo lỗi nêu rõ cần cài công cụ gì.

### 7. Điều khiển media máy kia
- Vị trí: thẻ "Điều khiển media máy kia".
- Thao tác: B bấm các nút phát/tạm dừng, bài trước/kế, âm lượng +/-, tắt tiếng.
- Kỳ vọng: nhận "Máy kia: … đã thực hiện". Trên Linux cần `playerctl` (phát nhạc) và `wpctl` hoặc `amixer` (âm lượng). Thiếu công cụ thì nhận thông báo lỗi tương ứng.

### 8. Gửi thông báo
- Vị trí: thẻ "Gửi thông báo".
- Thao tác: B nhập tiêu đề và nội dung, bấm **Gửi thông báo**.
- Kỳ vọng: A hiện thông báo trên máy (qua `notify-send`) và nhãn "Từ máy kia: …" trong SCFT. B nhận "Đã hiện thông báo trên máy này." Nếu không có `notify-send`, A chỉ hiện trong SCFT.

### 9. Gửi nhiều tệp
- Vị trí: thẻ "Gửi nhiều tệp" ở Tiện ích, hoặc vùng kéo-thả ở tab **Tệp & dữ liệu**.
- Thao tác: B chọn nhiều tệp (hoặc kéo nhiều tệp vào vùng), các tệp được gửi lần lượt.
- Kỳ vọng: tab Tệp & dữ liệu hiện tiến độ, tốc độ, thời gian còn lại. Khi xong, trạng thái là "Hoàn tất · máy kia đã xác minh SHA-256". Tệp nằm trong `<user.home>/Downloads/SCFT` của A.

### 10. Lịch sử hoạt động
- Vị trí: thẻ "Lịch sử hoạt động".
- Thao tác: gõ từ khóa vào ô tìm kiếm, ví dụ `clipboard`, `Ghi chú`, `Mở URL`. Xóa từ khóa để xem lại tất cả.
- Kỳ vọng: bộ đếm "x / y mục" thay đổi theo bộ lọc. Nút **Xóa lịch sử** xóa toàn bộ danh sách.
- Lưu ý: tìm `chat` không ra mục nào, vì mục chat được ghi nhãn "Ghi chú". Dùng `Ghi chú` để tìm.

## 3. Kết quả thực tế

Chạy ngày 14/11/2026 trên một máy Linux (GNOME, Wayland, JDK 21.0.13, JavaFX 21.0.5), hai instance trên cùng máy (A cổng 45461, B cổng 45462, thư mục riêng). Mọi thao tác được thực hiện trên đúng điều khiển của giao diện (xem phần hạn chế về cách thao tác). Các câu trả lời bên dưới là nội dung thật hiển thị trong ứng dụng.

| Chức năng | Kết quả | Ghi chú |
|---|---|---|
| Ghép nối bằng mã | Đạt | B kết nối tới `127.0.0.1:45461` với mã của A. |
| 1. Trạng thái & ping | Đạt | Ping 0 ms (loopback). Pin máy này 100%. Máy kia "không rõ" vì máy không có pin. Mạng hiện "LAN <IP>". |
| 2. Clipboard | Đạt | B gửi "Clipboard từ PC B: hai chiều OK". A nhận "Đã nhận và chép vào clipboard: …", clipboard A có đúng nội dung. |
| 3. Chat | Đạt | B gửi "Xin chào từ PC B", A nhận. A trả lời, B nhận "Máy kia: PC A đã nhận…". |
| 4. Tìm thiết bị | Đạt | A hiện "Máy kia đang tìm thiết bị này (bíp và rung)". Dừng báo thì nhãn biến mất. Tiếng bíp và rung không kiểm chứng được bằng tự động. |
| 5. Mở URL | Đạt | B nhận "Máy kia: Đã mở liên kết trên máy này." Trình duyệt trên máy đã mở `https://example.com/`. |
| 6. Chụp màn hình | Đạt một phần | Từ chối: B nhận "Người dùng từ chối". Cho phép: B nhận lỗi "Không chụp được màn hình. Trên Wayland cài grim hoặc gnome-screenshot; trên X11 cài imagemagick (import)." Máy này không có công cụ chụp nên chưa thấy ảnh thật. |
| 7. Media | Đạt một phần | Phát/tạm dừng: "Lỗi: Máy kia: Không khả dụng: máy này chưa cài playerctl". Tăng và giảm âm lượng: "Máy kia: … đã thực hiện" (tổng thay đổi bằng 0). |
| 8. Thông báo | Đạt | A nhận "Từ máy kia: Thử SCFT — Xin chào từ PC B". B nhận "Đã hiện thông báo trên máy này." |
| 9. Gửi nhiều tệp | Đạt | Gửi 2 tệp (1.000.000 byte và 1.540 byte). Cả hai "Hoàn tất · máy kia đã xác minh SHA-256". Trên A, SHA-256 của hai tệp khớp với bản gốc. |
| 10. Lịch sử | Đạt | Không lọc: "22 / 22 mục". Lọc `chat`: "0 / 22 mục" (xem lưu ý trong mục 2). |
| Ngắt kết nối | Đạt | B ngắt, nhãn trạng thái về "Chưa kết nối thiết bị". A cũng về trạng thái chưa kết nối và không tự kết nối lại. |

Ảnh chụp cửa sổ (chụp vùng nhìn thấy, không cuộn) nằm trong `<thư mục demo>/run1/`: `A-01` đến `A-07`, `B-01` đến `B-10`. Ví dụ `B-02-tienich-ketnoi.png` cho thấy thẻ trạng thái, `B-06-chup-man-hinh.png` cho thấy thông báo lỗi chụp màn hình.

Kết quả đầy đủ (từng dòng log có thời gian) nằm trong `<thư mục demo>/run1/driver-host.log` và `driver-ctrl.log`.

## 4. Hạn chế

- **Cách thao tác**: không có công cụ điều khiển chuột/bàn phím trên Wayland (không có xdotool, ydotool). Driver gọi trực tiếp `fire()` trên nút và `setText()` trên ô nhập, tức là cùng handler mà click hoặc gõ phím sẽ gọi. Không kiểm chứng được việc bố cục, hover, phím tắt, hay kéo-thả bằng chuột thật.
- **Gửi tệp**: nút "Gửi nhiều tệp" và vùng kéo-thả mở hộp chọn tệp hoặc nhận kéo-thả. Driver gọi thẳng `LinkService.send(File)`, là cùng hàm mà hộp chọn tệp gọi. Không kiểm chứng được hộp chọn tệp và kéo-thả thật.
- **Hai instance trên cùng một máy**: clipboard hệ thống dùng chung giữa A và B, nên việc đồng bộ clipboard chỉ kiểm chứng được một phần. Âm thanh, thông báo và trình duyệt cũng là của cùng máy.
- **Chụp màn hình**: máy thử không có grim, gnome-screenshot hay spectacle, và `import` (ImageMagick) không có hỗ trợ X11. Vì vậy không có ảnh chụp thật. Đây là lỗi môi trường, không phải lỗi mã nguồn.
- **Media**: `playerctl` không được cài, nên phát/tạm dừng báo lỗi. Âm lượng đã đổi tăng rồi giảm 5% trên máy thử.
- **Mở URL**: `xdg-open` đã mở một cửa sổ trình duyệt (Brave) với `https://example.com/` trên máy thử. Cửa sổ này cần đóng bằng tay.
- **Tệp & dữ liệu**: đã kiểm tra gửi và xác minh SHA-256. Chưa kiểm tra nút **Mở thư mục** (mở trình quản lý tệp), **Mở tệp**, **Xóa mục đã xong** và tiếp tục khi đứt kết nối.
- **Tab Màn hình**: không kiểm tra, vì cần điện thoại thật.
- **Giao diện**: nhãn "Pin máy kia: không rõ (máy không có pin hoặc chưa đọc được)" bị cắt bằng dấu "…" trong thẻ có chiều rộng cố định. Ô tìm kiếm lịch sử không tìm được "chat" vì nhãn hiển thị là "Ghi chú" (xem mục 2).
- **Ảnh chụp**: ảnh chỉ chụp vùng nhìn thấy của cửa sổ. Nội dung phía dưới phải cuộn mới thấy.
- **Chưa thử**: truyền tệp lớn trên mạng thật, đứt mạng giữa chừng, nhiều thiết bị cùng lúc, và Wi-Fi thật giữa hai máy.

## 5. Trạng thái Flip3

Kiểm tra lúc bắt đầu việc 1: `tailscale status` báo thiết bị z-flip3 **offline** (lần cuối thấy cách đây 11 giờ), và `adb devices` không có thiết bị nào. Vì vậy **không** chạy `adb connect` và **không** mở scrcpy. Demo trên điện thoại chưa thực hiện. Khi Flip3 online, các bước cần làm là: `adb connect 100.115.117.61:5555`, rồi `scrcpy --serial=100.115.117.61:5555` (không dùng `--camera`). Không đụng tới ADB server, TLS hay pairing.

## 6. Dọn dẹp

- Hai tiến trình SCFT do demo mở đã được dừng. Không còn tiến trình SCFT nào chạy.
- Không có tiến trình scrcpy nào được mở hay dừng.
- Dữ liệu demo (tệp nhận, ảnh chụp, log, driver) nằm trong thư mục scratchpad của phiên, không nằm trong repo.
- Không có commit nào được tạo.
