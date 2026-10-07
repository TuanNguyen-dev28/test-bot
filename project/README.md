# Giao diện tư vấn vải

Frontend HTML, CSS, JavaScript thuần; backend Java 21 và Spring Boot 3.5.16. Spring Boot phục vụ cả giao diện và API trên cùng địa chỉ, không cần chạy frontend riêng.

## Chạy trên Windows

Tại thư mục gốc chứa `data` và `project`, chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\project\run.ps1
```

Mở **http://localhost:8080**. Lần đầu cần Internet để Maven Wrapper tải Maven và các thư viện vào `.cache` trong workspace. Máy cần có JDK 21 và lệnh `java`. Dừng bằng `Ctrl+C`.

Nếu cổng 8080 đang được dùng, đặt `$env:SERVER_PORT = '8081'` trước khi chạy và mở `http://localhost:8081`.

## Cấu trúc

```text
data/
  intents.json                 # Nhóm câu hỏi và các cách hỏi mẫu
  fabrics.json                 # Giá, màu, khổ vải và tồn kho
project/
  front-end/
    index.html
    style.css
    app.js
  back-end/
    pom.xml
    mvnw.cmd                   # Maven Wrapper cho Windows
    src/main/java/com/example/fabricchat/
      FabricChatApplication.java
      FabricService.java       # Đọc JSON, nhận diện câu hỏi, tạo câu trả lời
      ChatController.java      # API
      ApiExceptionHandler.java # Lỗi input trả về JSON
    src/main/resources/application.properties
    src/test/java/com/example/fabricchat/ChatIntegrationTest.java
  run.ps1
```

## Luồng hoạt động

1. Người dùng gõ câu hỏi hoặc bấm câu hỏi gợi ý.
2. JavaScript gửi `POST /api/chat` với JSON `{"message":"Giá vải cotton bao nhiêu?"}`.
3. Java chuẩn hóa tiếng Việt, đối chiếu câu hỏi mẫu và từ khóa trong ba nhóm giá, màu, tồn kho.
4. Backend trả `{"answer":"Giá Cotton 100%: 85.000 đồng."}` để giao diện hiển thị.

`GET /api/fabrics` trả danh sách vải để hiển thị thẻ chất liệu. Câu hỏi rỗng, JSON sai hoặc câu hỏi quá 1.000 ký tự nhận HTTP 400 với trường `error`.

## Chỉnh sửa dữ liệu

`run.ps1` đọc trực tiếp hai file JSON trong thư mục `data` thông qua biến `FABRIC_DATA_DIR`. Sau khi sửa JSON, khởi động lại backend. Nếu chạy Maven trực tiếp mà không đặt biến này, backend dùng bản JSON được Maven sao chép vào ứng dụng khi build.

Khi sửa frontend, khởi động lại lệnh chạy để Maven sao chép HTML/CSS/JS mới. Không mở `index.html` bằng `file://`; hãy dùng địa chỉ localhost ở trên.

Giá và tồn kho hiện chưa có trường đơn vị trong dữ liệu, nên ứng dụng không tự thêm đơn vị mét hoặc kilôgam. Mẫu màu trên thẻ chỉ là minh họa. Chương trình nhận diện câu hỏi theo mẫu và từ khóa, chưa dùng mô hình AI hoặc API bên ngoài. Mỗi câu hỏi cần ghi tên vải; hội thoại chưa lưu sau khi tải lại trang.

## Kiểm thử và đóng gói

```powershell
cd .\project\back-end
.\mvnw.cmd test
.\mvnw.cmd package
java -jar .\target\fabric-chat-0.0.1-SNAPSHOT.jar
```

File JAR chứa cả frontend và bản JSON tại thời điểm build. Có thể đặt `FABRIC_DATA_DIR` là đường dẫn tuyệt đối đến thư mục JSON để dùng dữ liệu bên ngoài.

Tham khảo phiên bản: [Spring Boot 3.5 — yêu cầu hệ thống](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
