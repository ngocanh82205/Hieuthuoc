<?php
// Kiểm tra xem ứng dụng Spring Boot (port 8080) có đang chạy không
$host = '127.0.0.1';
$port = 8080;
$connection = @fsockopen($host, $port, $errno, $errstr, 1);

if (is_resource($connection)) {
    fclose($connection);
    // Nếu Spring Boot đang chạy, chuyển hướng trực tiếp vào website
    header("Location: http://localhost:8080/");
    exit();
}
?>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>VinaPharma - Kết nối XAMPP & MySQL</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <style>
        body {
            background: linear-gradient(135deg, #f0f7ff 0%, #e6f0fa 100%);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: system-ui, -apple-system, sans-serif;
        }
        .card {
            border: none;
            border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0, 70, 150, 0.1);
        }
        .badge-status {
            padding: 8px 16px;
            border-radius: 30px;
            font-size: 0.9rem;
        }
        .code-box {
            background: #1e293b;
            color: #38bdf8;
            border-radius: 8px;
            padding: 12px 16px;
            font-family: Consolas, monospace;
        }
    </style>
</head>
<body>
    <div class="container py-5" style="max-width: 680px;">
        <div class="card p-4 p-md-5">
            <div class="text-center mb-4">
                <div class="d-inline-flex p-3 rounded-circle bg-primary bg-opacity-10 text-primary mb-3">
                    <i class="bi bi-capsule fs-1"></i>
                </div>
                <h2 class="fw-bold text-dark">VinaPharma - Nhà Thuốc Trực Tuyến</h2>
                <p class="text-secondary">Dự án Java Spring Boot kết nối MySQL trên XAMPP</p>
            </div>

            <div class="mb-4">
                <h5 class="fw-semibold text-dark mb-3"><i class="bi bi-database-check text-success me-2"></i>Trạng thái Cơ Sở Dữ Liệu</h5>
                <ul class="list-group list-group-flush border rounded-3">
                    <li class="list-group-item d-flex justify-content-between align-items-center">
                        <span><i class="bi bi-hdd-network me-2 text-muted"></i>MySQL Host</span>
                        <span class="fw-bold">localhost:3306</span>
                    </li>
                    <li class="list-group-item d-flex justify-content-between align-items-center">
                        <span><i class="bi bi-database me-2 text-muted"></i>Tên Database SQL</span>
                        <span class="badge bg-primary fs-6">Hieuthuoc</span>
                    </li>
                    <li class="list-group-item d-flex justify-content-between align-items-center">
                        <span><i class="bi bi-filetype-sql me-2 text-muted"></i>File Export SQL</span>
                        <span class="fw-semibold text-success">Hieuthuoc.sql (đã lưu ở thư mục gốc)</span>
                    </li>
                    <li class="list-group-item d-flex justify-content-between align-items-center">
                        <span><i class="bi bi-shield-lock me-2 text-muted"></i>Tài khoản MySQL</span>
                        <span class="fw-semibold">root / (không mật khẩu)</span>
                    </li>
                </ul>
            </div>

            <div class="mb-4">
                <h5 class="fw-semibold text-dark mb-2"><i class="bi bi-play-circle text-primary me-2"></i>Cách khởi động ứng dụng Web</h5>
                <p class="text-secondary small mb-2">Mở terminal tại thư mục này và chạy lệnh:</p>
                <div class="code-box mb-3">mvnw.cmd spring-boot:run</div>
                <p class="text-secondary small">Sau khi khởi chạy, truy cập web tại: 
                    <a href="http://localhost:8080" target="_blank" class="fw-bold text-decoration-none">http://localhost:8080</a>
                </p>
            </div>

            <div class="d-grid gap-2 d-md-flex justify-content-md-center">
                <a href="http://localhost:8080" class="btn btn-primary px-4 py-2">
                    <i class="bi bi-box-arrow-up-right me-1"></i> Truy cập Website (Port 8080)
                </a>
                <a href="http://localhost/phpmyadmin/index.php?route=/database/structure&db=Hieuthuoc" target="_blank" class="btn btn-outline-secondary px-4 py-2">
                    <i class="bi bi-server me-1"></i> Mở phpMyAdmin (DB: Hieuthuoc)
                </a>
            </div>

            <hr class="my-4">
            <div class="small text-muted text-center">
                <strong>Tài khoản mẫu:</strong><br>
                Admin: <code>admin@hieuthuoc.vn</code> / <code>admin123</code> | 
                Dược sĩ: <code>duocsi@hieuthuoc.vn</code> / <code>duocsi123</code> | 
                Khách: <code>khachhang@gmail.com</code> / <code>123456</code>
            </div>
        </div>
    </div>
</body>
</html>
