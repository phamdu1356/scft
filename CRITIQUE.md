# Đánh giá UI/UX - SCFT

Phạm vi: `src/main/java/scft/Main.java`, `DevicesView.java`, `ScreenView.java`, `FilesView.java`, `TransferItem.java`, `Direction.java`, `LinkService.java` (chỉ phần hiển thị và trạng thái) và `src/main/resources/scft/app.css`.

Đây là bản đọc code, chưa chạy app. Các số liệu về kích thước là ước tính từ độ rộng nhãn và padding. Cần xác nhận bằng cách chạy thật ở cửa sổ 900x600 trước khi sửa.

Mức độ:
- **P0**: người dùng tin nhầm, hoặc mất chức năng chính, hoặc nội dung bị che khuất.
- **P1**: trải nghiệm kém rõ rệt, thiếu phản hồi, thiếu trạng thái.
- **P2**: đánh bóng, accessibility, nhất quán.

Tổng: **3 P0, 18 P1, 17 P2** (38 mục).

---

## P0

### 1. [P0] Chiếu màn hình chỉ là mô phỏng nhưng nút trông như thật
- Vị trí: `ScreenView.java:138-143`, `ScreenView.java:213`, `LinkService.java:242`
- Vấn đề: nút "Bắt đầu chiếu" / "Dừng chiếu" và khung điện thoại xanh đều giống chức năng thật. Lời cảnh báo "MÔ PHỎNG · chưa có nguồn hình thật" chỉ là chữ 11px ở góc dưới canvas. Người dùng có thể nghĩ màn hình đã được chiếu.
- Sửa: thêm banner rõ ràng phía trên stage ("Tính năng đang phát triển. Đây chỉ là hình minh họa.") và đổi nhãn nút thành "Chiếu thử (mô phỏng)" cho đến khi có nguồn hình thật. Ẩn nút khi chưa có tính năng thật nếu sản phẩm chưa sẵn sàng.

### 2. [P0] Thanh công cụ Màn hình tràn ở cửa sổ tối thiểu 900px
- Vị trí: `ScreenView.java:130-147`, `Main.java:66`
- Vấn đề: thanh công cụ là một `HBox` gồm hai nút chiều (mỗi nút chứa "Điện thoại → Máy tính" dài), `ChoiceBox` chất lượng, một `Region` co giãn và nút chính. Ước tính cần khoảng 750-800px. Nội dung khả dụng ở 900px là khoảng 900 - 220 (sidebar) - 56 (padding) ≈ 624px. Nút chính "Bắt đầu chiếu" nằm cuối nên có khả năng bị cắt mất.
- Sửa: đưa nút chính xuống hàng riêng hoặc dùng `FlowPane`. Rút gọn nhãn chiều thành "→ Phone" / "→ PC" hoặc chỉ dùng icon kèm tooltip. Đặt `minWidth` hợp lý cho từng control.

### 3. [P0] Lỗi mở cổng nhận bị che giấu, nhưng vẫn hiện mã ghép nối như thể sẵn sàng
- Vị trí: `LinkService.java:57-60`, `DevicesView.java:23-30`, `Main.java:92-104`
- Vấn đề: nếu cổng 45454 bị chiếm, lỗi chỉ được ghi vào `error` và chỉ hiển thị trong `DevicesView` (`DevicesView.java:56-61`). Thẻ "Máy này (chờ kết nối)" vẫn hiện địa chỉ và mã. Thanh trạng thái không hiển thị lỗi. Ở tab Tệp hoặc Màn hình, người dùng không biết máy không nhận được gì.
- Sửa: hiển thị banner lỗi đỏ ở đầu mọi màn hình khi `errorProperty` có giá trị và ở thanh trạng thái. Khi không bind được cổng, ẩn mã và hiện nút "Thử lại" cùng hướng dẫn xử lý xung đột cổng.

---

## P1

### 4. [P1] Màn Thiết bị không có cuộn; nội dung có thể bị cắt ở chiều cao 600px
- Vị trí: `DevicesView.java:86-87`, `Main.java:67`
- Vấn đề: `DevicesView` là `VBox` thuần, không có `ScrollPane`. Ước tính tổng chiều cao gồm tiêu đề, thẻ trạng thái, thẻ "Máy này" (kèm mã 36px), thẻ "Kết nối" (có hai trường, nút và lỗi) là khoảng 800px. Ở chiều cao tối thiểu 600px, phần dưới như nút "Kết nối" và dòng lỗi có thể bị cắt.
- Sửa: bọc nội dung trong `ScrollPane(fitToWidth=true)`, hoặc chuyển hai thẻ kết nối sang hai cột ở chiều rộng lớn và xếp dọc khi hẹp.

### 5. [P1] Vùng thả tệp bị vô hiệu hóa khi chưa kết nối nhưng không giải thích
- Vị trí: `FilesView.java:27`, `FilesView.java:35`, `app.css:13-15`
- Vấn đề: khi chưa kết nối, dropzone mờ 50% với cùng dòng chữ "Kéo tệp vào đây hoặc bấm để chọn". Người dùng bấm không có phản hồi và không biết cần kết nối trước.
- Sửa: đổi nội dung khi disabled thành "Kết nối một thiết bị để gửi tệp" và thêm nút "Đi tới Thiết bị".

### 6. [P1] Nút "Kết nối" không có trạng thái đang kết nối và có thể bấm nhiều lần
- Vị trí: `DevicesView.java:50-55`, `LinkService.java:93-124`
- Vấn đề: sau khi bấm, nút vẫn bật trong tối đa 5 giây timeout. Không có spinner, không có chữ "Đang kết nối…". Bấm lặp lại sẽ tạo nhiều socket.
- Sửa: bind `disable` vào cờ `connecting`, đổi nhãn thành "Đang kết nối…" kèm `ProgressIndicator` nhỏ, và thêm nút "Hủy" nếu có thể.

### 7. [P1] Thông báo lỗi lộ nguyên văn Java exception bằng tiếng Anh
- Vị trí: `LinkService.java:119` ("Không kết nối được tới … : " + `e.getMessage()`), `LinkService.java:177`, `LinkService.java:209` ("Lỗi: " + `e.getMessage()`)
- Vấn đề: người dùng thấy "Connection refused: connect" hoặc "Socket closed" giữa câu tiếng Việt. Đây là lỗi copy và cũng làm khó chẩn đoán.
- Sửa: ánh xạ các loại `IOException` thành thông điệp tiếng Việt cố định ("Không tìm thấy thiết bị tại địa chỉ này", "Hết thời gian chờ", "Kết nối đã bị đóng"). Giữ chi tiết kỹ thuật trong log hoặc trong một mục "Chi tiết" có thể mở rộng.

### 8. [P1] Địa chỉ hiển thị "không rõ:45454" khi không tìm được IP
- Vị trí: `LinkService.java:281`, `DevicesView.java:26`
- Vấn đề: `findLocalIp()` trả về "không rõ" và `DevicesView` nối thêm `:45454`. Kết quả là chuỗi vô nghĩa "không rõ:45454" và người dùng không biết phải làm gì.
- Sửa: khi không có IP, hiện "Không xác định được địa chỉ mạng. Kiểm tra Wi-Fi hoặc Ethernet." và vô hiệu hóa thẻ mã. Dùng thêm `InetAddress` cho địa chỉ thứ hai nếu có nhiều NIC, và cho người dùng chọn.

### 9. [P1] Mã ghép nối và địa chỉ không sao chép được; không có nút làm mới mã
- Vị trí: `DevicesView.java:25-30`, `app.css:9`, `app.css:16`
- Vấn đề: `Label` không cho chọn văn bản. Người dùng phải đọc và gõ lại 6 chữ số và địa chỉ IP. Mã chỉ đổi khi ngắt kết nối (`LinkService.java:235`), không có nút tạo mã mới khi bị lộ.
- Sửa: thêm nút "Sao chép" cạnh địa chỉ và mã, và nút "Tạo mã mới" với icon `fth-refresh-cw`. Dùng `TextField` không sửa được, hoặc bật `Label.setTextOverrun`/selection nếu phù hợp.

### 10. [P1] Thẻ thiết bị khi chưa kết nối hiện "—" và icon điện thoại; trạng thái trống yếu
- Vị trí: `DevicesView.java:68-84`, `LinkService.java:25-26`
- Vấn đề: khi không có kết nối, thẻ lớn nhất trên cùng hiện dấu gạch ngang "—" làm tên thiết bị, cộng với icon điện thoại. Không có lời mời hành động rõ ràng.
- Sửa: khi không kết nối, hiện thẻ trung tính "Chưa có thiết bị nào được kết nối" kèm một dòng hướng dẫn. Chỉ hiện icon điện thoại khi thực sự có thiết bị.

### 11. [P1] Ngắt kết nối không xác nhận, không cảnh báo khi đang truyền tệp, trạng thái truyền bị kẹt
- Vị trí: `DevicesView.java:74-77`, `LinkService.java:217-237`, `LinkService.java:162`, `LinkService.java:207`
- Vấn đề: nút "Ngắt kết nối" (màu đỏ) có tác dụng ngay. Nếu đang truyền, tệp dở dang và hàng truyền có thể kẹt ở "Đang nhận" hoặc "Đang gửi" cho đến khi lỗi socket xảy ra. Đây không phải trạng thái cuối cùng rõ ràng.
- Sửa: nếu có truyền đang chạy, hiện hộp thoại xác nhận "Đang truyền N tệp. Ngắt kết nối sẽ hủy các tệp này?". Khi ngắt, đánh dấu mọi hàng đang chạy là "Đã hủy" và đặt tiến trình về trạng thái rõ ràng.

### 12. [P1] Tệp nhận được tự động ghi vào thư mục Downloads, không hỏi xác nhận
- Vị trí: `LinkService.java:156-181`
- Vấn đề: bất kỳ tệp nào từ thiết bị đã ghép nối đều được ghi ngay vào `~/Downloads/SCFT`. Không có hộp thoại chấp nhận, không kiểm tra dung lượng trống, không giới hạn kích thước. Đây là vấn đề về niềm tin và bảo mật.
- Sửa: hiện thông báo "Thiết bị X muốn gửi tệp Y (Z MB). Chấp nhận / Từ chối" trước khi ghi. Thêm tùy chọn "Luôn chấp nhận từ thiết bị này" và kiểm tra dung lượng trống trước khi nhận.

### 13. [P1] Hàng truyền chỉ có icon mũi tên, không có nhãn Gửi/Nhận và không có hành động sau khi xong
- Vị trí: `FilesView.java:65-80`, `TransferItem.java:98`
- Vấn đề: chiều truyền chỉ thể hiện bằng icon `arrow-down-circle` / `arrow-up-circle`, không có chữ và không có tooltip. Sau khi trạng thái thành "Đã lưu: tên", không có cách mở tệp hoặc mở thư mục chứa nó. Không có nút xóa các hàng đã xong.
- Sửa: thêm nhãn "Nhận từ điện thoại" / "Gửi tới điện thoại" hoặc tiền tố "↓ Nhận" / "↑ Gửi". Thêm nút "Mở thư mục" và "Xóa mục đã xong". Hiện phần trăm bên cạnh thanh tiến trình.

### 14. [P1] Thả nhiều tệp: trạng thái "Đang gửi" không đúng, và thư mục gây lỗi
- Vị trí: `LinkService.java:183-215`, `FilesView.java:44-51`
- Vấn đề: mỗi tệp tạo một thread riêng và tất cả chờ cùng một khóa `synchronized (o)`. Tệp đang chờ vẫn hiện "Đang gửi" và 0%. Thư mục có `length()` = 0 và `FileInputStream` sẽ ném lỗi, hiện "Lỗi: …" bằng tiếng Anh. Không có kiểm tra gì trước khi nhận tệp.
- Sửa: dùng hàng đợi gửi tuần tự và trạng thái "Đang chờ" đúng nghĩa. Từ chối thư mục ngay ở `FilesView` với thông báo "Chỉ gửi được tệp, không gửi được thư mục".

### 15. [P1] Ô "Chất lượng" không làm gì nhưng trông như điều khiển thật
- Vị trí: `ScreenView.java:133-135`
- Vấn đề: `ChoiceBox` có ba lựa chọn nhưng không bind vào `LinkService` và không ảnh hưởng đến gì. Ngoài ra không có nhãn "Chất lượng" đứng trước, nên người dùng không biết đó là gì.
- Sửa: hoặc ẩn đến khi có chức năng, hoặc thêm nhãn "Chất lượng" và vô hiệu hóa kèm tooltip "Có sau khi có nguồn hình thật".

### 16. [P1] Đổi chiều khi đang chiếu làm trạng thái bị lỗi thời
- Vị trí: `ScreenView.java:125-129`, `LinkService.java:239-244`
- Vấn đề: đổi chiều chỉ đặt `directionProperty` và resize khung. Chuỗi trạng thái "Đang chiếu (mô phỏng): …" chỉ được cập nhật trong `toggleMirroring()`, nên thanh trạng thái vẫn ghi chiều cũ.
- Sửa: lắng nghe `directionProperty` trong `LinkService` và cập nhật `statusText`, hoặc tạm dừng chiếu khi đổi chiều và báo người dùng bấm lại.

### 17. [P1] Vùng Màn hình hẹp: thông báo trong canvas không xuống dòng và bị cắt
- Vị trí: `ScreenView.java:204-206`, `ScreenView.java:169-175`
- Vấn đề: `fillText` đặt chuỗi "Chưa có thiết bị. Vào mục Thiết bị để ghép nối" trên một dòng. Khi cửa sổ ở mức tối thiểu, khung điện thoại rộng khoảng 200px nên chuỗi dài khoảng 360px bị cắt hai bên. `resize()` cũng để khung điện thoại quá nhỏ mà không có chế độ dự phòng.
- Sửa: tự ngắt dòng bằng cách chia chuỗi theo độ rộng đo được, hoặc chuyển thông báo ra `Label` ngoài canvas với `wrapText`. Đặt kích thước tối thiểu cho khung.

### 18. [P1] AnimationTimer vẽ canvas 60fps mãi mãi, kể cả khi đang ở tab khác
- Vị trí: `ScreenView.java:156-158`
- Vấn đề: timer được khởi động trong constructor và không bao giờ dừng. Khi người dùng ở tab Thiết bị hoặc Tệp, canvas vẫn bị vẽ lại liên tục, tốn CPU và pin, nhất là trên laptop.
- Sửa: lưu `AnimationTimer`, gọi `start()` khi `ScreenView` được hiển thị (listener trên `sceneProperty`/`visibleProperty`) và `stop()` khi bị ẩn. Hoặc chỉ vẽ khi `live` là true.

### 19. [P1] Nút chuyển giao diện có nhãn không đổi theo trạng thái
- Vị trí: `Main.java:52-54`, `Main.java:28`
- Vấn đề: nút ghi "Giao diện tối" khi đang bật và khi tắt. Người dùng không biết đang ở chế độ nào, và chữ "Giao diện tối" nghe như hành động mở chế độ tối nhưng bấm xong vẫn cùng chữ.
- Sửa: đổi nhãn theo trạng thái ("Chuyển sang giao diện tối" khi đang sáng, "Chuyển sang giao diện sáng" khi đang tối), hoặc dùng `ToggleSwitch` có nhãn "Giao diện tối" và trạng thái bật/tắt rõ ràng.

### 20. [P1] Hướng dẫn ghép nối thiếu bước tường lửa, nguyên nhân phổ biến nhất
- Vị trí: `DevicesView.java:31`
- Vấn đề: gợi ý chỉ nói "Hai thiết bị phải cùng mạng". Trên Windows, tường lửa thường chặn cổng 45454 nên kết nối thất bại mà không rõ lý do. Không có liên kết trợ giúp.
- Sửa: thêm dòng "Nếu không kết nối được, cho phép SCFT qua tường lửa (cổng 45454, TCP)" và nút "Xem hướng dẫn" nếu có.

### 21. [P1] Nút "Bắt đầu chiếu" bị vô hiệu hóa mà không có lời giải thích
- Vị trí: `ScreenView.java:142`
- Vấn đề: khi chưa kết nối, nút mờ đi nhưng không có tooltip hoặc dòng chữ nào nói "Kết nối thiết bị trước". Người dùng không biết vì sao không bấm được.
- Sửa: thêm `Tooltip` cho nút ("Kết nối một thiết bị để bắt đầu chiếu") và có thể hiện chính dòng này bên cạnh nút.

---

## P2

### 22. [P2] Màn Thiết bị: nửa bên phải trống
- Vị trí: `DevicesView.java:37`, `DevicesView.java:65`, `DevicesView.java:84`, `DevicesView.java:87`
- Vấn đề: các thẻ bị giới hạn 520px và xếp dọc trong `HBox(20, left)`. Ở cửa sổ 1100px, khoảng 300px bên phải trống.
- Sửa: dùng hai cột: trái là "Máy này", phải là "Kết nối tới thiết bị khác", hoặc cho thẻ co giãn theo chiều rộng.

### 23. [P2] Thứ tự thông tin trên màn Thiết bị chưa đúng ưu tiên
- Vị trí: `DevicesView.java:67-87`
- Vấn đề: thẻ trạng thái kết nối nằm trên cùng, trong khi thông tin cần thao tác nhất (mã ghép nối và form kết nối) nằm bên dưới.
- Sửa: đặt thanh trạng thái gọn ở trên, sau đó là hai thẻ thao tác chính. Thẻ kết nối đang hoạt động chỉ hiện khi có kết nối.

### 24. [P2] Thiếu phím tắt, mnemonic và tooltip cho điều hướng; nav dùng ToggleButton
- Vị trí: `Main.java:42-47`, `Main.java:79-90`, `app.css:2-4`
- Vấn đề: không có Ctrl+1/2/3 hoặc phím tắt khác. `.nav-btn` không có style focus riêng. Các mục điều hướng là `ToggleButton`, không có vai trò tab, nên trình đọc màn hình không nhận ra đây là danh sách chuyển màn.
- Sửa: thêm `KeyCombination` Ctrl+1/2/3 cho ba màn, đặt `accessibleRole` là TAB hoặc dùng `TabPane` ẩn đầu, và thêm tooltip mô tả.

### 25. [P2] Nhãn trong thẻ "Máy này" không gắn với điều khiển nào
- Vị trí: `DevicesView.java:34`
- Vấn đề: "Địa chỉ" và "Mã ghép nối" là `Label` thường, không dùng `setLabelFor`. Các giá trị hiển thị bằng `Label` nên trình đọc màn hình đọc rời rạc. Hai nhãn "Mã ghép nối" xuất hiện ở hai thẻ với ý nghĩa khác nhau (hiển thị và nhập).
- Sửa: gộp thành cặp nhãn-giá trị có vai trò rõ ràng, và đổi nhãn bên nhập thành "Nhập mã từ thiết bị kia".

### 26. [P2] Cỡ chữ cố định theo px, không theo DPI hoặc cài đặt phóng to
- Vị trí: `app.css:9`, `app.css:16`, `ScreenView.java:202`, `ScreenView.java:211`
- Vấn đề: `pair-code` 36px, `pair-addr` 18px và chữ canvas 14px/11px cố định, không đổi khi người dùng tăng cỡ chữ hệ thống. Chữ 11px trong canvas quá nhỏ.
- Sửa: dùng `em` hoặc tỷ lệ theo `title-*` của AtlantaFX. Tăng chữ caption trong canvas lên ít nhất 12-13px.

### 27. [P2] Font "monospace" có thể không khớp trên Windows
- Vị trí: `app.css:9`, `app.css:16`
- Vấn đề: `"monospace"` không phải tên font chắc chắn trên mọi hệ điều hành. Trên Windows mã có thể rơi về font tỷ lệ, làm mã khó đọc.
- Sửa: khai báo danh sách như `"Cascadia Mono", "Consolas", "Menlo", monospace`.

### 28. [P2] Vùng thả tệp mờ 50% khi disabled làm giảm tương phản
- Vị trí: `app.css:15`
- Vấn đề: `-fx-opacity: 0.5` làm cả chữ lẫn viền mờ, dưới ngưỡng tương phản WCAG cho văn bản.
- Sửa: giữ độ mờ của viền và nền, còn chữ dùng `-color-fg-muted` với độ tương phản đạt yêu cầu. Chữ phải vẫn đọc được.

### 29. [P2] Chấm trạng thái chỉ dựa vào màu
- Vị trí: `app.css:6-7`, `Main.java:97-98`
- Vấn đề: chấm xanh và xám 10px là tín hiệu duy nhất ở một phần. Nhãn chữ có ở thanh trạng thái, nhưng chấm không có vai trò trợ năng và người mù màu khó phân biệt kích thước nhỏ.
- Sửa: đổi chấm thành icon có hình khác nhau (tick, vòng tròn rỗng), và đặt tooltip.

### 30. [P2] Tiêu đề "Tệp & dữ liệu" không đúng nội dung; đường dẫn thư mục dài không xuống dòng và không mở được
- Vị trí: `FilesView.java:20`, `FilesView.java:23`
- Vấn đề: tiêu đề nói "dữ liệu" trong khi màn chỉ quản lý tệp. Dòng "Tệp nhận được lưu tại: …" không có `wrapText`, đường dẫn dài trên Windows có thể bị cắt hoặc đẩy layout.
- Sửa: đổi tiêu đề thành "Tệp" và bật `wrapText`. Thêm liên kết "Mở thư mục" để mở `inbox` trong trình quản lý tệp.

### 31. [P2] ListCell tạo lại node và bind mới mỗi lần cập nhật
- Vị trí: `FilesView.java:61-80`
- Vấn đề: `updateItem` tạo `Label`, `ProgressBar` mới và bind vào thuộc tính của `TransferItem` mỗi lần cell được tái sử dụng. Bind cũ không được gỡ, nên có nguy cơ rò rỉ listener khi danh sách dài.
- Sửa: tạo node một lần trong constructor của cell, chỉ cập nhật nội dung trong `updateItem`, và unbind khi cell rỗng.

### 32. [P2] Ô nhập IP và mã không kiểm tra định dạng; Enter không cho biết lý do
- Vị trí: `DevicesView.java:42-55`
- Vấn đề: IP không được kiểm tra, khoảng trắng chỉ có vẫn được tính là có dữ liệu (`isEmpty()` là false). Mã có thể nhập chữ và không có giới hạn 6 chữ số. Nút "Kết nối" mờ và Enter không làm gì mà không báo lý do.
- Sửa: dùng `TextFormatter` giới hạn ký tự và độ dài mã, kiểm tra IPv4 trước khi bật nút, và hiển thị dòng lỗi nhỏ ngay dưới ô nhập.

### 33. [P2] Canvas không có tên hoặc mô tả cho trình đọc màn hình
- Vị trí: `ScreenView.java:109`
- Vấn đề: vùng chiếu là `Canvas`, không có `accessibleText`. Trình đọc màn hình không biết trạng thái đang chiếu hay đang chờ.
- Sửa: đặt `accessibleText` cho stage, cập nhật theo trạng thái ("Đang chiếu mô phỏng", "Chưa kết nối").

### 34. [P2] Giao diện mặc định là sáng, không theo cài đặt hệ thống
- Vị trí: `Main.java:28`, `Main.java:33`
- Vấn đề: `dark` khởi đầu là `false` cố định. Người dùng đã bật chế độ tối trên hệ điều hành vẫn thấy giao diện sáng khi mở app.
- Sửa: đọc tùy chọn hệ thống khi khởi động (ví dụ `Platform.getPreferences()` ở JavaFX mới, hoặc đọc từ cấu hình) và lưu lựa chọn người dùng.

### 35. [P2] Thanh chọn chiều: góc bo bị mất và nhãn dài
- Vị trí: `app.css:11`, `Direction.java:118-119`
- Vấn đề: `.segmented > .toggle-button { -fx-background-radius: 0 }` bỏ bo cả hai đầu của nhóm. Nhãn "Điện thoại → Máy tính" quá dài cho một nút trong thanh công cụ hẹp.
- Sửa: bo góc đầu và cuối riêng (`:first-child`, `:last-child`). Rút nhãn hoặc dùng cặp icon kèm tooltip đầy đủ.

### 36. [P2] Nút Bắt đầu và Dừng chiếu dùng cùng màu nhấn
- Vị trí: `ScreenView.java:141`, `app.css:12`
- Vấn đề: nút "Dừng chiếu" và "Bắt đầu chiếu" đều có class `accent`, nên không có khác biệt thị giác về trạng thái. Chỉ icon và nhãn thay đổi.
- Sửa: dùng màu trung tính hoặc danger cho "Dừng chiếu" để tín hiệu trạng thái rõ hơn.

### 37. [P2] Lỗi kết nối nằm xa trường nhập
- Vị trí: `DevicesView.java:56-62`
- Vấn đề: dòng lỗi nằm dưới nút "Kết nối", xa hai ô nhập mà người dùng cần sửa. Lỗi cũng không được liên kết với ô nào.
- Sửa: đặt lỗi ngay dưới ô địa chỉ hoặc ô mã, và đưa focus về ô đó. Dùng màu và icon, không chỉ màu chữ đỏ.

### 38. [P2] Chữ caption trong canvas trên nền gradient có độ tương phản thấp khi có lớp trắng
- Vị trí: `ScreenView.java:189-196`, `ScreenView.java:210-213`
- Vấn đề: chữ `#F8FAFC` 11px đặt trên gradient xanh, và các dải trắng 14% làm nền sáng hơn trong vài vùng. Độ tương phản ở đây sát ngưỡng 4.5:1 cho chữ nhỏ và còn thấp hơn ở các vùng có lớp trắng.
- Sửa: thêm nền tối mờ phía sau caption (rect bán trong suốt) và tăng cỡ chữ lên 12-13px.

---

## Tóm tắt ưu tiên sửa

1. Làm rõ trạng thái mô phỏng ở Màn hình (mục 1), sửa thanh công cụ tràn (mục 2) và hiện lỗi cổng (mục 3). Đây là ba việc P0.
2. Với Thiết bị: cuộn, trạng thái đang kết nối, copy/làm mới mã, thông báo lỗi tiếng Việt (mục 4, 6, 7, 9).
3. Với Tệp: xác nhận trước khi nhận, hàng truyền có nhãn và hành động, hàng đợi gửi đúng (mục 12, 13, 14).
4. Cuối cùng là các điểm đánh bóng P2.

Tổng: **3 P0, 18 P1, 17 P2**.

---

## Trạng thái sửa (cập nhật sau khi sửa code)

Biên dịch: `mvn -q compile -Dmaven.compiler.release=17` (JDK 21 trên máy này chỉ có JRE, không có javac, nên dùng JDK 17 để kiểm tra; mã không dùng API mới hơn 17). Chưa chạy app thật để kiểm tra bố cục ở 900x600: cần xác nhận bằng cách chạy.

Ký hiệu: **Đã sửa** / **Một phần** / **Bỏ qua** (kèm lý do).

### P0
- 1. **Đã sửa.** Banner vàng "Tính năng đang phát triển" và badge "MÔ PHỎNG" trên đầu màn hình; nút đổi thành "Chiếu thử (mô phỏng)"; canvas ghi chú "MÔ PHỎNG". Nút không bị ẩn.
- 2. **Đã sửa.** Thanh công cụ là `FlowPane` (tự xuống dòng); nhãn chiều rút gọn "Điện thoại → PC" kèm tooltip; mọi view nằm trong `ScrollPane`.
- 3. **Đã sửa.** Cổng bận: banner đỏ ở mọi màn hình với nút "Thử lại", màn Thiết bị ẩn mã và địa chỉ thay bằng cảnh báo. Lỗi kết nối hiện ngay dưới ô mã (xem mục 37); banner không lặp lại ở màn Thiết bị.

### P1
- 4. **Đã sửa.** Mọi view trong `ScrollPane` (fitToWidth).
- 5. **Đã sửa.** Khi chưa kết nối, vùng thả hiện "Kết nối một thiết bị để gửi tệp" và có nút "Đi tới Thiết bị".
- 6. **Một phần.** Nút hiện "Đang kết nối…" kèm spinner, bị khóa khi đang kết nối (không gửi socket thứ hai). Chưa có nút "Hủy": `LinkService` không hỗ trợ hủy, chỉ có timeout 5 giây.
- 7. **Đã sửa.** Lỗi socket được đổi sang thông báo tiếng Việt cố định (`LinkService.friendly`); chi tiết kỹ thuật ghi ra stderr.
- 8. **Đã sửa.** Không tìm được IP thì hiện hướng dẫn kiểm tra Wi-Fi, không còn "không rõ:45454". Chọn IP khi có nhiều card mạng: **chưa làm**.
- 9. **Đã sửa.** Địa chỉ và mã là ô chỉ đọc (chọn/copy được), có nút "Sao chép" và "Tạo mã mới". Mã mới chỉ đổi trên máy này.
- 10. **Đã sửa.** Khi chưa kết nối, thẻ trung tính "Chưa có thiết bị nào được kết nối" kèm hướng dẫn; thẻ thiết bị chỉ hiện khi đã kết nối.
- 11. **Một phần.** Ngắt kết nối lúc đang truyền có hộp xác nhận; các hàng đang chạy được đánh dấu "Đã hủy do ngắt kết nối" và tệp nhận dở bị xóa. Chưa có cách hủy từng tệp.
- 12. **Bỏ qua.** Cần bước chấp nhận/từ chối phía người nhận, tức là thay đổi giao thức; yêu cầu là giữ nguyên giao thức `LinkService`. Chưa kiểm tra dung lượng trống.
- 13. **Đã sửa.** Nhãn "Nhận từ điện thoại" / "Gửi tới điện thoại", phần trăm, nút "Mở tệp" (tệp đã nhận), "Mở thư mục", "Xóa mục đã xong".
- 14. **Đã sửa.** Gửi tuần tự qua một luồng (`sendQueue`); tệp chờ hiện "Đang chờ"; thư mục bị từ chối với thông báo tiếng Việt.
- 15. **Đã sửa (ẩn).** Bỏ ô "Chất lượng" vì chưa có chức năng.
- 16. **Đã sửa.** Đổi chiều khi đang chiếu thì dừng chiếu và ghi rõ trong thanh trạng thái.
- 17. **Đã sửa.** Thông báo rỗng là `Label` có xuống dòng, không còn vẽ chữ lên canvas.
- 18. **Đã sửa.** `AnimationTimer` chỉ chạy khi tab Màn hình đang mở (`setActive`).
- 19. **Đã sửa.** Nhãn và icon nút giao diện đổi theo trạng thái; lựa chọn được lưu.
- 20. **Đã sửa một phần.** Có dòng nhắc tường lửa cổng 45454; chưa có nút "Xem hướng dẫn".
- 21. **Đã sửa.** Dòng "Kết nối một thiết bị trước..." hiện cạnh nút. Không dùng tooltip vì nút bị vô hiệu hóa thì tooltip không hiện.

### P2
- 22. **Đã sửa.** Hai thẻ nằm trong `FlowPane`: cạnh nhau khi rộng, xếp dọc khi hẹp.
- 23. **Đã sửa.** Thẻ trạng thái (hoặc thẻ trung tính) ở trên, các thẻ thao tác ở dưới.
- 24. **Một phần.** Ctrl+1/2/3, tooltip và viền focus cho nav. Vai trò trợ năng vẫn là `ToggleButton`, chưa đổi sang TAB.
- 25. **Đã sửa.** `setLabelFor` cho ô địa chỉ và mã; nhãn bên nhập đổi thành "Mã ghép nối trên thiết bị kia".
- 26. **Một phần.** Chữ caption trên canvas 13px. Cỡ chữ `pair-code`/`pair-addr` vẫn theo px, chưa theo cỡ chữ hệ thống.
- 27. **Đã sửa, cần kiểm tra trên Windows.** Danh sách họ font (Cascadia Mono, Consolas, Menlo...). Chưa xác nhận JavaFX dùng được danh sách này.
- 28. **Đã sửa.** Vùng thả bị vô hiệu hóa không dùng `opacity`; chữ và viền dùng màu muted.
- 29. **Đã sửa.** Trạng thái dùng icon khác hình (vòng tròn / dấu tick) và màu.
- 30. **Đã sửa.** Tiêu đề "Tệp", đường dẫn xuống dòng, có nút "Mở thư mục".
- 31. **Đã sửa.** Node của cell tạo một lần; bind được gỡ khi cell tái sử dụng.
- 32. **Đã sửa.** `TextFormatter` chỉ cho số và tối đa 6 chữ số; kiểm tra IPv4; dòng lý do cạnh nút.
- 33. **Đã sửa.** `accessibleText` của canvas cập nhật theo trạng thái (chỉ khi đổi).
- 34. **Một phần.** Lưu lựa chọn sáng/tối (`java.util.prefs`). Chưa đọc theme hệ thống: JavaFX 21 không có `Platform.getPreferences()`, cần JavaFX 22 trở lên.
- 35. **Đã sửa.** Bo góc đầu và cuối nhóm; nhãn rút gọn, tooltip đầy đủ.
- 36. **Đã sửa.** "Dừng chiếu thử" dùng màu danger, "Chiếu thử" dùng accent.
- 37. **Đã sửa một phần.** Lỗi kết nối nằm ngay dưới ô mã, có icon. Chưa tự đưa focus về ô nhập.
- 38. **Đã sửa.** Caption có nền tối bán trong suốt, chữ 13px.

### Thay đổi kèm theo
- `LinkService`: thêm `listenErrorProperty`, `retryListen`, `connectingProperty`, `stopMirroring`, `regeneratePairingCode`, `hasActiveTransfers`, `clearFinished`. `findLocalIp()` trả về "" thay vì "không rõ". Giao thức (magic, mã, khung T_FILE/T_BYE, thứ tự đọc/ghi) không đổi.
- `FilesView` nhận thêm tham số `goDevices` (constructor mới).
