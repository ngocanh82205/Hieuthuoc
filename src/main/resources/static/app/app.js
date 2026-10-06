/*
 * VinaPharma App - ứng dụng client (SPA) tiêu thụ REST API /api/v1.
 * - Định tuyến phía client bằng hash (#/products, #/cart...).
 * - Xác thực: POST /auth/login -> accessToken, gửi kèm header "Authorization: Bearer <token>".
 * - Giỏ hàng lưu ở client (localStorage), mỗi lần thay đổi gọi POST /cart/quote để server tính giá.
 * - Mọi lời gọi API đi qua hàm api(): hiển thị thanh loading, chuẩn hóa lỗi (401/403/404/409/422/500).
 */
(function () {
    'use strict';

    var BASE = location.pathname.replace(/app\/.*$/, '');
    var API = BASE + 'api/v1';
    var $view = document.getElementById('view');
    var pending = 0;

    /* ---------------- Lưu trữ phía client ---------------- */
    var store = {
        get: function (k, def) {
            try { var v = localStorage.getItem('vp.' + k); return v ? JSON.parse(v) : def; } catch (e) { return def; }
        },
        set: function (k, v) {
            try { v == null ? localStorage.removeItem('vp.' + k) : localStorage.setItem('vp.' + k, JSON.stringify(v)); } catch (e) { /* bỏ qua */ }
        }
    };
    var auth = { token: store.get('token'), user: store.get('user') };
    var cart = store.get('cart', []);

    function saveAuth(token, user) {
        auth = { token: token, user: user };
        store.set('token', token);
        store.set('user', user);
        renderNav();
    }

    function saveCart() {
        store.set('cart', cart);
        renderNav();
    }

    /* ---------------- Gọi API ---------------- */
    function ApiError(status, message, errors) {
        this.status = status;
        this.message = message;
        this.errors = errors || [];
    }

    function loading(on) {
        pending += on ? 1 : -1;
        document.getElementById('loading').classList.toggle('d-none', pending <= 0);
    }

    /** @param body object (gửi JSON) | FormData (gửi multipart) | undefined */
    function api(method, path, body) {
        var headers = { Accept: 'application/json' };
        if (auth.token) headers.Authorization = 'Bearer ' + auth.token;
        var opts = { method: method, headers: headers };
        if (body instanceof FormData) {
            opts.body = body;
        } else if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(body);
        }
        loading(true);
        return fetch(API + path, opts)
            .then(function (res) {
                return res.json().catch(function () { return {}; }).then(function (j) {
                    if (res.ok && j.success !== false) return j;
                    if (res.status === 401 && auth.token) {
                        saveAuth(null, null);
                        toast('warning', 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.');
                        location.hash = '#/login?next=' + encodeURIComponent(location.hash);
                    }
                    throw new ApiError(res.status, j.message || ('Lỗi ' + res.status), j.errors);
                });
            }, function () {
                throw new ApiError(0, 'Không kết nối được máy chủ. Kiểm tra mạng và thử lại.');
            })
            .finally(function () { loading(false); });
    }

    /* ---------------- Tiện ích giao diện ---------------- */
    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    /** Ảnh sản phẩm; sản phẩm chưa có ảnh hiện biểu tượng thay thế. */
    function img(p, cls) {
        return p.image ? '<img src="' + esc(BASE.replace(/\/$/, '') + p.image) + '" class="' + cls + '" alt="' + esc(p.name) + '">'
            : '<div class="' + cls + ' img-ph d-flex align-items-center justify-content-center"><i class="bi bi-capsule fs-1 text-primary opacity-50"></i></div>';
    }

    function money(n) { return (Number(n) || 0).toLocaleString('vi-VN') + ' ₫'; }

    function dt(s) { return s ? new Date(s).toLocaleString('vi-VN') : ''; }

    function toast(type, msg) {
        var el = document.createElement('div');
        el.className = 'toast align-items-center text-bg-' + (type === 'error' ? 'danger' : type) + ' border-0';
        el.setAttribute('role', 'alert');
        el.innerHTML = '<div class="d-flex"><div class="toast-body">' + esc(msg) + '</div>'
            + '<button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Đóng"></button></div>';
        document.getElementById('toasts').appendChild(el);
        new bootstrap.Toast(el, { delay: 4000 }).show();
        el.addEventListener('hidden.bs.toast', function () { el.remove(); });
    }

    /** Khối báo lỗi: thông điệp + danh sách lỗi kiểm tra dữ liệu (422). */
    function errorHtml(err) {
        var list = (err.errors || []).map(function (e) { return '<li>' + esc(e) + '</li>'; }).join('');
        return '<div class="alert alert-danger"><i class="bi bi-exclamation-triangle me-1"></i>' + esc(err.message)
            + (list ? '<ul class="mb-0 mt-1">' + list + '</ul>' : '') + '</div>';
    }

    function render(html) { $view.innerHTML = html; window.scrollTo(0, 0); }

    function skeleton(rows) {
        var h = '';
        for (var i = 0; i < (rows || 4); i++) h += '<div class="skeleton mb-3" style="height:' + (i ? 60 : 32) + 'px"></div>';
        render(h);
    }

    function fail(err) {
        if (err.status === 401) return;
        render(errorHtml(err) + '<a href="#/" class="btn btn-outline-primary">Về trang chủ</a>');
    }

    function query() {
        var q = {};
        var s = location.hash.split('?')[1] || '';
        s.split('&').forEach(function (p) {
            if (!p) return;
            var kv = p.split('=');
            q[decodeURIComponent(kv[0])] = decodeURIComponent((kv[1] || '').replace(/\+/g, ' '));
        });
        return q;
    }

    function formData(form) {
        var o = {};
        new FormData(form).forEach(function (v, k) { if (!(v instanceof File)) o[k] = v; });
        return o;
    }

    /** Gửi form: khóa nút, hiện lỗi ngay trên form, gọi onOk khi thành công. */
    function submit(form, call, onOk) {
        var btn = form.querySelector('[type=submit]');
        var box = form.querySelector('.form-errors');
        if (btn) btn.disabled = true;
        if (box) box.innerHTML = '';
        call().then(function (j) {
            if (j.message) toast('success', j.message);
            onOk(j);
        }, function (err) {
            if (box) box.innerHTML = errorHtml(err); else toast('error', err.message);
        }).finally(function () { if (btn) btn.disabled = false; });
    }

    function requireLogin(role) {
        if (!auth.token) {
            location.hash = '#/login?next=' + encodeURIComponent(location.hash);
            return false;
        }
        if (role && role.indexOf(auth.user.role) < 0) {
            render('<div class="alert alert-warning">Chức năng này không dành cho tài khoản của bạn.</div>');
            return false;
        }
        return true;
    }

    /* ---------------- Thanh điều hướng ---------------- */
    function renderNav() {
        var u = auth.user;
        var role = u ? u.role : null;
        var main = [['#/', 'bi-grid', 'Sản phẩm']];
        if (!role || role === 'CUSTOMER') {
            main.push(['#/cart', 'bi-cart3', 'Giỏ hàng <span class="badge text-bg-light">' + cart.reduce(function (s, l) { return s + l.quantity; }, 0) + '</span>']);
        }
        if (role === 'CUSTOMER') main.push(['#/orders', 'bi-receipt', 'Đơn hàng'], ['#/consult', 'bi-chat-dots', 'Tư vấn']);
        if (role === 'PHARMACIST' || role === 'ADMIN') main.push(['#/staff', 'bi-clipboard2-pulse', 'Nghiệp vụ']);
        if (role === 'ADMIN') main.push(['#/admin/products', 'bi-box-seam', 'Quản lý sản phẩm']);
        document.getElementById('navMain').innerHTML = main.map(function (m) {
            return '<li class="nav-item"><a class="nav-link" href="' + m[0] + '"><i class="bi ' + m[1] + '"></i> ' + m[2] + '</a></li>';
        }).join('');
        document.getElementById('navUser').innerHTML = u
            ? '<li class="nav-item"><a class="nav-link" href="#/account"><i class="bi bi-person-circle"></i> ' + esc(u.fullName) + '</a></li>'
            + '<li class="nav-item"><a class="nav-link" href="#/logout"><i class="bi bi-box-arrow-right"></i> Đăng xuất</a></li>'
            : '<li class="nav-item"><a class="nav-link" href="#/login">Đăng nhập</a></li><li class="nav-item"><a class="nav-link" href="#/register">Đăng ký</a></li>';
    }

    /* ================= Màn hình: danh sách sản phẩm ================= */
    function productCard(p) {
        return '<div class="col-6 col-md-4 col-lg-3"><div class="card h-100 product-card">'
            + '<a href="#/products/' + esc(p.slug) + '">' + img(p, 'card-img-top p-2') + '</a>'
            + '<div class="card-body d-flex flex-column"><span class="badge text-bg-' + (p.prescription ? 'danger' : 'success') + ' align-self-start mb-1">' + esc(p.drugType.label) + '</span>'
            + '<a class="name text-decoration-none text-body fw-semibold" href="#/products/' + esc(p.slug) + '">' + esc(p.name) + '</a>'
            + '<div class="mt-auto"><span class="price">' + money(p.flashPrice || p.price) + '</span><small class="text-muted">/' + esc(p.unit) + '</small>'
            + (p.oldPrice ? ' <del class="small text-muted">' + money(p.oldPrice) + '</del>' : '')
            + '<div class="small ' + (p.available > 0 ? 'text-success' : 'text-danger') + '">' + (p.available > 0 ? 'Còn hàng' : 'Hết hàng') + '</div></div></div></div></div>';
    }

    function pager(meta, base) {
        if (!meta || meta.lastPage <= 1) return '';
        var h = '<nav><ul class="pagination justify-content-center mt-4">';
        for (var i = 1; i <= meta.lastPage; i++) {
            h += '<li class="page-item' + (i === meta.page ? ' active' : '') + '"><a class="page-link" href="' + base + 'page=' + i + '">' + i + '</a></li>';
        }
        return h + '</ul></nav>';
    }

    function pageProducts() {
        var q = query();
        skeleton(6);
        Promise.all([api('GET', '/categories'), api('GET', '/products?' + new URLSearchParams(q).toString())]).then(function (r) {
            var cats = r[0].data, list = r[1];
            var base = '#/?' + new URLSearchParams(Object.assign({}, q, { page: '' })).toString().replace(/page=$/, '').replace(/&$/, '');
            render('<form class="row g-2 mb-3" id="filter">'
                + '<div class="col-md-4"><input name="q" class="form-control" placeholder="Tìm tên thuốc, hoạt chất..." value="' + esc(q.q) + '"></div>'
                + '<div class="col-md-3"><select name="category" class="form-select"><option value="">Mọi danh mục</option>'
                + cats.map(function (c) { return '<option value="' + esc(c.slug) + '"' + (q.category === c.slug ? ' selected' : '') + '>' + '— '.repeat(c.depth) + esc(c.name) + '</option>'; }).join('')
                + '</select></div><div class="col-md-3"><select name="sort" class="form-select">'
                + [['bestseller', 'Bán chạy'], ['price_asc', 'Giá tăng dần'], ['price_desc', 'Giá giảm dần'], ['newest', 'Mới nhất'], ['name', 'Tên A-Z']].map(function (s) {
                    return '<option value="' + s[0] + '"' + (q.sort === s[0] ? ' selected' : '') + '>' + s[1] + '</option>';
                }).join('')
                + '</select></div><div class="col-md-2"><button class="btn btn-primary w-100"><i class="bi bi-search"></i> Lọc</button></div></form>'
                + '<p class="text-muted small">' + list.meta.total + ' sản phẩm</p>'
                + (list.data.length ? '<div class="row g-3">' + list.data.map(productCard).join('') + '</div>' : '<div class="alert alert-info">Không tìm thấy sản phẩm phù hợp.</div>')
                + pager(list.meta, base + (base.indexOf('?') === base.length - 1 ? '' : '&')));
            document.getElementById('filter').addEventListener('submit', function (e) {
                e.preventDefault();
                var p = new URLSearchParams(formData(e.target));
                location.hash = '#/?' + p.toString();
            });
        }, fail);
    }

    /* ================= Màn hình: chi tiết sản phẩm ================= */
    function pageProduct(slug) {
        skeleton(5);
        Promise.all([api('GET', '/products/' + encodeURIComponent(slug)), api('GET', '/products/' + encodeURIComponent(slug) + '/reviews')]).then(function (r) {
            var d = r[0].data, p = d.product, reviews = r[1].data;
            var canBuy = d.sellableOnline && p.available > 0 && (!auth.user || auth.user.role === 'CUSTOMER');
            render('<a href="#/" class="small">← Sản phẩm</a><div class="row g-4 mt-1">'
                + '<div class="col-md-5"><div class="card">' + img(p, 'card-img-top p-3') + '</div></div>'
                + '<div class="col-md-7"><h1 class="h4">' + esc(p.name) + '</h1>'
                + '<span class="badge text-bg-' + (p.prescription ? 'danger' : 'success') + '">' + esc(p.drugType.label) + '</span> '
                + (d.promotions || []).map(function (x) { return '<span class="badge text-bg-warning">' + esc(x) + '</span>'; }).join(' ')
                + '<div class="my-3"><span class="price fs-4">' + money(p.flashPrice || p.price) + '</span> / ' + esc(p.unit) + '</div>'
                + '<table class="table table-sm small"><tbody>'
                + [['Hoạt chất', p.activeIngredient + ' ' + (p.strength || '')], ['Dạng bào chế', d.dosageForm], ['Quy cách', d.packaging], ['Số đăng ký', d.registrationNo],
                    ['Nhà sản xuất', d.manufacturer], ['Xuất xứ', d.country]].filter(function (x) { return x[1] && x[1] !== 'null '; }).map(function (x) {
                    return '<tr><th class="text-muted fw-normal" style="width:35%">' + x[0] + '</th><td>' + esc(x[1]) + '</td></tr>';
                }).join('') + '</tbody></table>'
                + (p.prescription ? '<div class="alert alert-warning small">Thuốc kê đơn: cần tải ảnh đơn thuốc khi đặt hàng, dược sĩ duyệt trước khi giao.</div>' : '')
                + (canBuy ? '<form id="addCart" class="d-flex gap-2"><select name="unitId" class="form-select" style="max-width:220px">'
                    + d.units.map(function (u) { return '<option value="' + u.id + '" data-name="' + esc(u.name) + '">' + esc(u.name) + ' - ' + money(u.price) + '</option>'; }).join('')
                    + '</select><input type="number" name="quantity" value="1" min="1" class="form-control" style="max-width:90px">'
                    + '<button type="submit" class="btn btn-primary"><i class="bi bi-cart-plus"></i> Thêm vào giỏ</button></form>'
                    : '<div class="text-muted">' + (p.available > 0 ? 'Sản phẩm không bán online.' : 'Tạm hết hàng.') + '</div>')
                + '</div></div>'
                + section('Công dụng', d.description) + section('Cách dùng', d.usageInstruction) + section('Chống chỉ định', d.contraindications) + section('Tác dụng phụ', d.sideEffects)
                + '<h2 class="h5 mt-4">Đánh giá (' + r[1].meta.total + ')</h2>'
                + (reviews.length ? reviews.map(function (rv) {
                    return '<div class="border-bottom py-2"><strong>' + esc(rv.user) + '</strong> <span class="text-warning">' + '★'.repeat(rv.rating) + '</span>'
                        + (rv.verifiedPurchase ? ' <span class="badge text-bg-success-subtle text-success-emphasis">Đã mua</span>' : '') + '<div>' + esc(rv.comment) + '</div></div>';
                }).join('') : '<p class="text-muted">Chưa có đánh giá.</p>')
                + (d.related.length ? '<h2 class="h5 mt-4">Thuốc tương đương</h2><div class="row g-3">' + d.related.map(productCard).join('') + '</div>' : ''));
            var f = document.getElementById('addCart');
            if (f) f.addEventListener('submit', function (e) {
                e.preventDefault();
                var sel = f.unitId.options[f.unitId.selectedIndex];
                addToCart({ productId: p.id, unitId: Number(f.unitId.value), quantity: Number(f.quantity.value) || 1, name: p.name, unitName: sel.dataset.name, slug: p.slug });
            });
        }, fail);
    }

    function section(title, text) {
        return text ? '<h2 class="h6 mt-4">' + title + '</h2><p class="small" style="white-space:pre-line">' + esc(text) + '</p>' : '';
    }

    function addToCart(line) {
        var hit = cart.find(function (l) { return l.productId === line.productId && l.unitId === line.unitId; });
        if (hit) hit.quantity += line.quantity; else cart.push(line);
        saveCart();
        toast('success', 'Đã thêm ' + line.quantity + ' ' + line.unitName + ' "' + line.name + '" vào giỏ hàng.');
    }

    /* ================= Màn hình: giỏ hàng (báo giá từ server) ================= */
    function quoteBody(extra) {
        return Object.assign({
            items: cart.map(function (l) { return { productId: l.productId, unitId: l.unitId, quantity: l.quantity }; }),
            voucher: store.get('voucher', ''), usePoints: store.get('usePoints', false)
        }, extra || {});
    }

    function pageCart() {
        if (!cart.length) return render('<div class="text-center py-5"><i class="bi bi-cart3 fs-1 text-muted"></i><p>Giỏ hàng trống.</p><a href="#/" class="btn btn-primary">Mua sắm ngay</a></div>');
        if (!requireLogin(['CUSTOMER'])) return;
        skeleton(4);
        api('POST', '/cart/quote', quoteBody({ province: 'Hà Nội' })).then(function (j) {
            var v = j.data;
            render('<h1 class="h4 mb-3">Giỏ hàng</h1><div class="row g-4"><div class="col-lg-8"><div class="card"><ul class="list-group list-group-flush">'
                + v.lines.map(function (l, i) {
                    return '<li class="list-group-item d-flex flex-wrap gap-2 align-items-center"><div class="flex-grow-1"><div class="fw-semibold">' + esc(l.name) + '</div>'
                        + '<small class="text-muted">' + money(l.unitPrice) + '/' + esc(l.unit) + '</small>' + (l.prescription ? ' <span class="badge text-bg-danger">Kê đơn</span>' : '')
                        + (l.error ? '<div class="small text-danger">' + esc(l.error) + '</div>' : '') + '</div>'
                        + '<input type="number" min="0" class="form-control form-control-sm" style="width:80px" value="' + l.quantity + '" data-qty="' + i + '" aria-label="Số lượng">'
                        + '<strong style="width:110px" class="text-end">' + money(l.lineTotal) + '</strong></li>';
                }).join('') + '</ul></div>'
                + (v.errors.length ? '<div class="alert alert-warning mt-3">' + v.errors.map(esc).join('<br>') + '</div>' : '') + '</div>'
                + '<div class="col-lg-4"><div class="card card-body">'
                + '<form id="voucher" class="input-group mb-2"><input name="code" class="form-control" placeholder="Mã giảm giá" value="' + esc(v.voucher || store.get('voucher', '')) + '"><button class="btn btn-outline-primary" type="submit">Áp dụng</button></form>'
                + (v.voucherError ? '<div class="small text-danger mb-2">' + esc(v.voucherError) + '</div>' : '')
                + (v.pointsAvailable ? '<div class="form-check mb-2"><input class="form-check-input" type="checkbox" id="pts"' + (store.get('usePoints', false) ? ' checked' : '') + '><label class="form-check-label" for="pts">Dùng ' + v.pointsAvailable + ' điểm</label></div>' : '')
                + row('Tạm tính', money(v.subtotal)) + (v.promoDiscount ? row('Khuyến mãi', '-' + money(v.promoDiscount)) : '') + (v.discount ? row('Giảm giá', '-' + money(v.discount)) : '')
                + (v.pointsDiscount ? row('Dùng điểm', '-' + money(v.pointsDiscount)) : '') + row('Phí giao hàng (tạm tính)', money(v.shippingFee))
                + '<hr>' + row('<strong>Tổng cộng</strong>', '<strong class="price">' + money(v.total) + '</strong>')
                + '<a href="#/checkout" class="btn btn-primary w-100 mt-3' + (v.errors.length ? ' disabled' : '') + '">Đặt hàng</a></div></div></div>');
            $view.querySelectorAll('[data-qty]').forEach(function (inp) {
                inp.addEventListener('change', function () {
                    var i = Number(inp.dataset.qty), n = Number(inp.value);
                    if (n <= 0) cart.splice(i, 1); else cart[i].quantity = n;
                    saveCart();
                    pageCart();
                });
            });
            document.getElementById('voucher').addEventListener('submit', function (e) {
                e.preventDefault();
                store.set('voucher', e.target.code.value.trim().toUpperCase());
                pageCart();
            });
            var pts = document.getElementById('pts');
            if (pts) pts.addEventListener('change', function () { store.set('usePoints', pts.checked); pageCart(); });
        }, fail);
    }

    function row(a, b) { return '<div class="d-flex justify-content-between"><span>' + a + '</span><span>' + b + '</span></div>'; }

    /* ================= Màn hình: đặt hàng ================= */
    function pageCheckout() {
        if (!requireLogin(['CUSTOMER'])) return;
        if (!cart.length) { location.hash = '#/cart'; return; }
        skeleton(5);
        Promise.all([api('GET', '/store'), api('GET', '/me/addresses'), api('GET', '/me'), api('POST', '/cart/quote', quoteBody())]).then(function (r) {
            var st = r[0].data, addrs = r[1].data, me = r[2].data.user, quote = r[3].data;
            var a = addrs[0] || {};
            render('<h1 class="h4 mb-3">Đặt hàng</h1><form id="checkout" class="row g-4" enctype="multipart/form-data"><div class="col-lg-7"><div class="card card-body">'
                + '<div class="form-errors"></div>'
                + '<div class="mb-2"><label class="form-label">Hình thức nhận</label><select name="shippingMethod" class="form-select"><option value="DELIVERY">Giao tận nơi</option><option value="PICKUP">Nhận tại nhà thuốc</option></select></div>'
                + (addrs.length ? '<div class="mb-2"><label class="form-label">Sổ địa chỉ</label><select id="addrPick" class="form-select">' + addrs.map(function (x, i) {
                    return '<option value="' + i + '">' + esc(x.recipient + ' - ' + x.fullText) + '</option>';
                }).join('') + '</select></div>' : '')
                + '<div class="row g-2"><div class="col-md-6"><label class="form-label">Người nhận</label><input name="recipient" class="form-control" value="' + esc(a.recipient || me.fullName) + '"></div>'
                + '<div class="col-md-6"><label class="form-label">Điện thoại</label><input name="phone" class="form-control" value="' + esc(a.phone || me.phone) + '"></div>'
                + '<div class="col-md-5"><label class="form-label">Tỉnh/thành</label><select name="province" class="form-select">' + st.provinces.map(function (p) {
                    return '<option' + (p === (a.province || 'Hà Nội') ? ' selected' : '') + '>' + esc(p) + '</option>';
                }).join('') + '</select></div>'
                + '<div class="col-md-7"><label class="form-label">Địa chỉ</label><input name="address" class="form-control" value="' + esc(a.addressLine || '') + '"></div></div>'
                + '<div class="my-2"><label class="form-label">Thanh toán</label>' + st.paymentMethods.map(function (m, i) {
                    return '<div class="form-check"><input class="form-check-input" type="radio" name="paymentMethod" id="pm' + i + '" value="' + m.code + '"' + (i ? '' : ' checked') + '><label class="form-check-label" for="pm' + i + '">' + esc(m.label) + '</label></div>';
                }).join('') + '</div>'
                + (quote.prescriptionRequired ? '<div class="alert alert-warning small mb-2">Giỏ hàng có thuốc kê đơn - tải lên ảnh đơn thuốc.</div><input type="file" name="prescription" accept="image/*" class="form-control mb-2" required>' : '')
                + '<textarea name="note" class="form-control" rows="2" placeholder="Ghi chú cho nhà thuốc"></textarea></div></div>'
                + '<div class="col-lg-5"><div class="card card-body">' + quote.lines.map(function (l) { return row(esc(l.quantity + ' ' + l.unit + ' ' + l.name), money(l.lineTotal)); }).join('')
                + '<hr>' + row('Tạm tính', money(quote.subtotal)) + (quote.discount + quote.promoDiscount + quote.pointsDiscount ? row('Giảm', '-' + money(quote.discount + quote.promoDiscount + quote.pointsDiscount)) : '')
                + '<p class="small text-muted mt-2">Phí giao hàng được tính chính xác khi đặt.</p>'
                + '<button type="submit" class="btn btn-primary w-100">Xác nhận đặt hàng</button></div></div></form>');
            var pick = document.getElementById('addrPick');
            var form = document.getElementById('checkout');
            if (pick) pick.addEventListener('change', function () {
                var x = addrs[pick.value];
                form.recipient.value = x.recipient; form.phone.value = x.phone; form.address.value = x.addressLine; if (x.province) form.province.value = x.province;
            });
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                var body = Object.assign(quoteBody(), formData(form));
                var call;
                if (quote.prescriptionRequired) {
                    // Có thuốc kê đơn: gửi multipart kèm ảnh đơn thuốc
                    var fd = new FormData(form);
                    body.items.forEach(function (it, i) {
                        fd.append('items[' + i + '][productId]', it.productId);
                        fd.append('items[' + i + '][unitId]', it.unitId);
                        fd.append('items[' + i + '][quantity]', it.quantity);
                    });
                    if (body.voucher) fd.append('voucher', body.voucher);
                    if (body.usePoints) fd.append('usePoints', '1');
                    call = function () { return api('POST', '/orders', fd); };
                } else {
                    call = function () { return api('POST', '/orders', body); };
                }
                submit(form, call, function (j) {
                    cart = []; saveCart(); store.set('voucher', null); store.set('usePoints', null);
                    var o = j.data.order;
                    if (j.data.nextAction === 'PAY_ONLINE') return pay(o.code);
                    location.hash = '#/orders/' + o.code;
                });
            });
        }, fail);
    }

    function pay(code) {
        api('POST', '/orders/' + code + '/payment').then(function (j) {
            location.href = BASE.replace(/\/$/, '') + j.data.paymentUrl.replace(BASE.replace(/\/$/, ''), '');
        }, function (err) { toast('error', err.message); location.hash = '#/orders/' + code; });
    }

    /* ================= Màn hình: đơn hàng ================= */
    function statusBadge(c) { return '<span class="badge text-bg-secondary">' + esc(c.label) + '</span>'; }

    function pageOrders() {
        if (!requireLogin(['CUSTOMER'])) return;
        var q = query();
        skeleton(5);
        api('GET', '/orders?page=' + (q.page || 1)).then(function (j) {
            render('<h1 class="h4 mb-3">Đơn hàng của tôi</h1>' + (j.data.length ? '<div class="list-group">' + j.data.map(function (o) {
                return '<a class="list-group-item list-group-item-action d-flex justify-content-between" href="#/orders/' + esc(o.code) + '"><div><strong>' + esc(o.code) + '</strong> '
                    + statusBadge(o.status) + '<div class="small text-muted">' + dt(o.createdAt) + ' · ' + o.itemCount + ' sản phẩm · ' + esc(o.paymentStatus.label) + '</div></div>'
                    + '<strong>' + money(o.total) + '</strong></a>';
            }).join('') + '</div>' : '<p class="text-muted">Bạn chưa có đơn hàng.</p>') + pager(j.meta, '#/orders?'));
        }, fail);
    }

    function pageOrder(code) {
        if (!requireLogin(['CUSTOMER'])) return;
        skeleton(5);
        api('GET', '/orders/' + encodeURIComponent(code)).then(function (j) {
            var d = j.data, o = d.order;
            render('<a href="#/orders" class="small">← Đơn hàng</a><div class="d-flex flex-wrap justify-content-between align-items-center my-2"><h1 class="h4 mb-0">Đơn ' + esc(o.code) + '</h1><div>' + statusBadge(o.status) + ' '
                + '<span class="badge text-bg-info">' + esc(o.paymentStatus.label) + '</span></div></div>'
                + '<div class="row g-4"><div class="col-lg-7"><div class="card"><ul class="list-group list-group-flush">' + o.items.map(function (i) {
                    return '<li class="list-group-item d-flex justify-content-between"><span>' + esc(i.productName) + ' <small class="text-muted">x' + i.quantity + ' ' + esc(i.unit) + '</small>'
                        + (i.gift ? ' <span class="badge text-bg-success">Quà tặng</span>' : '') + '</span><span>' + money(i.lineTotal) + '</span></li>';
                }).join('') + '</ul><div class="card-body">' + row('Tạm tính', money(o.subtotal)) + row('Giảm giá', '-' + money(o.discount + o.promoDiscount + o.pointsDiscount))
                + row('Phí giao hàng', money(o.shippingFee)) + row('<strong>Tổng cộng</strong>', '<strong class="price">' + money(o.total) + '</strong>') + '</div></div>'
                + '<div class="d-flex gap-2 mt-3">' + (d.canPay ? '<button class="btn btn-success" id="btnPay"><i class="bi bi-credit-card"></i> Thanh toán online</button>' : '')
                + (d.canCancel ? '<button class="btn btn-outline-danger" id="btnCancel">Hủy đơn</button>' : '') + '</div>'
                + (d.bankTransferQr ? '<div class="card card-body mt-3 text-center"><p class="small mb-2">Quét mã để chuyển khoản</p><img src="' + esc(d.bankTransferQr) + '" alt="QR chuyển khoản" style="max-width:240px" class="mx-auto"></div>' : '')
                + '</div><div class="col-lg-5"><div class="card card-body small"><div><strong>Người nhận:</strong> ' + esc(o.recipient) + ' · ' + esc(o.phone) + '</div>'
                + (o.address ? '<div><strong>Địa chỉ:</strong> ' + esc(o.address + (o.province ? ', ' + o.province : '')) + '</div>' : '<div>Nhận tại nhà thuốc</div>')
                + '<div><strong>Thanh toán:</strong> ' + esc(o.paymentMethod.label) + '</div></div>'
                + '<div class="card card-body mt-3"><h2 class="h6">Lịch sử xử lý</h2><ul class="small mb-0">' + o.history.map(function (h) {
                    return '<li><strong>' + esc(h.status.label) + '</strong> · ' + dt(h.at) + (h.note ? '<div class="text-muted">' + esc(h.note) + '</div>' : '') + '</li>';
                }).join('') + '</ul></div></div></div>');
            var bp = document.getElementById('btnPay');
            if (bp) bp.addEventListener('click', function () { pay(o.code); });
            var bc = document.getElementById('btnCancel');
            if (bc) bc.addEventListener('click', function () {
                var reason = prompt('Lý do hủy đơn?', 'Đổi ý');
                if (reason === null) return;
                api('POST', '/orders/' + o.code + '/cancel', { reason: reason }).then(function (r) { toast('success', r.message); pageOrder(code); }, function (err) { toast('error', err.message); });
            });
        }, fail);
    }

    /* ================= Màn hình: đăng nhập / đăng ký / tài khoản ================= */
    function pageLogin() {
        var next = query().next || '#/';
        render('<div class="row justify-content-center"><div class="col-md-5"><div class="card card-body"><h1 class="h4">Đăng nhập</h1>'
            + '<form id="login"><div class="form-errors"></div><div class="mb-3"><label class="form-label">Email hoặc số điện thoại</label><input name="login" class="form-control" autocomplete="username"></div>'
            + '<div class="mb-3"><label class="form-label">Mật khẩu</label><input type="password" name="password" class="form-control" autocomplete="current-password"></div>'
            + '<button type="submit" class="btn btn-primary w-100">Đăng nhập</button></form><p class="small mt-3 mb-0">Chưa có tài khoản? <a href="#/register">Đăng ký</a></p></div></div></div>');
        var f = document.getElementById('login');
        f.addEventListener('submit', function (e) {
            e.preventDefault();
            submit(f, function () { return api('POST', '/auth/login', formData(f)); }, function (j) {
                saveAuth(j.data.accessToken, j.data.user);
                location.hash = j.data.user.role === 'CUSTOMER' ? next : '#/staff';
            });
        });
    }

    function pageRegister() {
        render('<div class="row justify-content-center"><div class="col-md-6"><div class="card card-body"><h1 class="h4">Đăng ký tài khoản</h1><form id="reg"><div class="form-errors"></div>'
            + [['fullName', 'Họ tên', 'text'], ['phone', 'Số điện thoại', 'tel'], ['email', 'Email (không bắt buộc)', 'email'], ['password', 'Mật khẩu', 'password'], ['passwordConfirmation', 'Nhập lại mật khẩu', 'password']].map(function (x) {
                return '<div class="mb-2"><label class="form-label">' + x[1] + '</label><input type="' + x[2] + '" name="' + x[0] + '" class="form-control"></div>';
            }).join('') + '<button type="submit" class="btn btn-primary w-100 mt-2">Đăng ký</button></form></div></div></div>');
        var f = document.getElementById('reg');
        f.addEventListener('submit', function (e) {
            e.preventDefault();
            submit(f, function () { return api('POST', '/auth/register', formData(f)); }, function (j) { saveAuth(j.data.accessToken, j.data.user); location.hash = '#/'; });
        });
    }

    function pageAccount() {
        if (!requireLogin()) return;
        skeleton(4);
        var isCustomer = auth.user.role === 'CUSTOMER';
        Promise.all([api('GET', '/auth/me'), isCustomer ? api('GET', '/me/addresses') : Promise.resolve({ data: [] })]).then(function (r) {
            var u = r[0].data, addrs = r[1].data;
            render('<h1 class="h4 mb-3">Tài khoản</h1><div class="row g-4"><div class="col-lg-6"><div class="card card-body"><h2 class="h6">Thông tin cá nhân</h2>'
                + (isCustomer ? '<form id="profile"><div class="form-errors"></div>'
                    + [['fullName', 'Họ tên', u.fullName], ['phone', 'Điện thoại', u.phone], ['email', 'Email', u.email], ['birthday', 'Ngày sinh (yyyy-mm-dd)', u.birthday]].map(function (x) {
                        return '<div class="mb-2"><label class="form-label small">' + x[1] + '</label><input name="' + x[0] + '" class="form-control" value="' + esc(x[2]) + '"></div>';
                    }).join('') + '<button type="submit" class="btn btn-primary">Lưu</button></form>'
                    : '<p>' + esc(u.fullName) + ' · ' + esc(u.position) + '</p><p class="small">Quyền: ' + u.permissions.map(esc).join(', ') + '</p>')
                + '</div></div>' + (isCustomer ? '<div class="col-lg-6"><div class="card card-body"><h2 class="h6">Sổ địa chỉ</h2><ul class="list-group mb-3">' + addrs.map(function (a) {
                    return '<li class="list-group-item d-flex justify-content-between"><span>' + esc(a.recipient + ' · ' + a.phone) + '<div class="small text-muted">' + esc(a.fullText) + '</div></span>'
                        + '<button class="btn btn-sm btn-outline-danger align-self-start" data-del="' + a.id + '" aria-label="Xóa địa chỉ"><i class="bi bi-trash"></i></button></li>';
                }).join('') + '</ul><form id="addr"><div class="form-errors"></div><div class="row g-2">'
                    + '<div class="col-6"><input name="recipient" class="form-control form-control-sm" placeholder="Người nhận"></div><div class="col-6"><input name="phone" class="form-control form-control-sm" placeholder="Điện thoại"></div>'
                    + '<div class="col-12"><input name="addressLine" class="form-control form-control-sm" placeholder="Số nhà, đường, phường/xã, quận/huyện"></div>'
                    + '<div class="col-8"><input name="province" class="form-control form-control-sm" placeholder="Tỉnh/thành"></div><div class="col-4"><button type="submit" class="btn btn-sm btn-primary w-100">Thêm</button></div></div></form></div></div>' : '')
                + '</div>');
            var pf = document.getElementById('profile');
            if (pf) pf.addEventListener('submit', function (e) {
                e.preventDefault();
                submit(pf, function () { return api('PUT', '/me', formData(pf)); }, function (j) { saveAuth(auth.token, j.data); });
            });
            var af = document.getElementById('addr');
            if (af) af.addEventListener('submit', function (e) {
                e.preventDefault();
                submit(af, function () { return api('POST', '/me/addresses', formData(af)); }, pageAccount);
            });
            $view.querySelectorAll('[data-del]').forEach(function (b) {
                b.addEventListener('click', function () {
                    if (!confirm('Xóa địa chỉ này?')) return;
                    api('DELETE', '/me/addresses/' + b.dataset.del).then(function (j) { toast('success', j.message); pageAccount(); }, function (err) { toast('error', err.message); });
                });
            });
        }, fail);
    }

    /* ================= Màn hình: tư vấn (polling) ================= */
    var chatTimer = null, lastId = 0;

    function pageConsult() {
        if (!requireLogin(['CUSTOMER'])) return;
        lastId = 0;
        render('<h1 class="h4 mb-3">Tư vấn cùng dược sĩ</h1><div class="card"><div class="card-header d-flex justify-content-between"><span id="chatMode">Đang tải...</span>'
            + '<button class="btn btn-sm btn-outline-primary" id="handoff">Gặp dược sĩ</button></div>'
            + '<div class="chat-box" id="chatBox"></div><form id="chatForm" class="card-footer d-flex gap-2"><input name="body" class="form-control" placeholder="Nhập câu hỏi..." autocomplete="off" required>'
            + '<button type="submit" class="btn btn-primary"><i class="bi bi-send"></i></button></form></div>');
        var box = document.getElementById('chatBox');
        function poll() {
            if (!document.getElementById('chatBox')) return clearInterval(chatTimer);
            api('GET', '/consult/messages?after=' + lastId).then(function (j) {
                var d = j.data;
                document.getElementById('chatMode').innerHTML = d.mode === 'AI' ? '<i class="bi bi-robot"></i> Trợ lý AI' : '<i class="bi bi-person-badge"></i> ' + esc(d.pharmacist || 'Đang chờ dược sĩ');
                d.messages.forEach(function (m) {
                    lastId = Math.max(lastId, m.id);
                    var mine = m.senderId === auth.user.id;
                    box.insertAdjacentHTML('beforeend', '<div class="bubble ' + (mine ? 'me' : 'other') + '">' + esc(m.body || '[Ảnh]') + '</div>');
                });
                if (d.messages.length) box.scrollTop = box.scrollHeight;
            }, function () { /* lỗi mạng tạm thời: lần poll sau thử lại */ });
        }
        clearInterval(chatTimer);
        poll();
        chatTimer = setInterval(poll, 3000);
        document.getElementById('chatForm').addEventListener('submit', function (e) {
            e.preventDefault();
            var fd = new FormData(e.target);
            e.target.reset();
            api('POST', '/consult/messages', fd).then(poll, function (err) { toast('error', err.message); });
        });
        document.getElementById('handoff').addEventListener('click', function () {
            api('POST', '/consult/handoff').then(function (j) { toast('info', j.message); poll(); }, function (err) { toast('error', err.message); });
        });
    }

    /* ================= Màn hình: nghiệp vụ nhân viên ================= */
    function pageStaff() {
        if (!requireLogin(['PHARMACIST', 'ADMIN'])) return;
        skeleton(5);
        var perms = auth.user.permissions;
        Promise.all([api('GET', '/staff/dashboard'), perms.indexOf('RX_REVIEW') >= 0 ? api('GET', '/staff/prescriptions') : Promise.resolve({ data: [] }),
            perms.indexOf('ORDER') >= 0 ? api('GET', '/staff/orders?status=PENDING') : Promise.resolve({ data: [] })]).then(function (r) {
            var d = r[0].data;
            var stat = function (v, l, c) { return '<div class="col-6 col-lg-3"><div class="card card-body"><div class="fs-4 fw-bold text-' + c + '">' + v + '</div><div class="small text-muted">' + l + '</div></div></div>'; };
            render('<h1 class="h4 mb-3">Nghiệp vụ nhà thuốc</h1><div class="row g-3 mb-4">' + stat(d.pendingPrescriptions, 'Đơn thuốc chờ duyệt', 'danger')
                + stat(d.pendingOrders, 'Đơn chờ xác nhận', 'primary') + stat(d.inProgressOrders, 'Đang xử lý / giao', 'success') + stat(d.nearExpiryBatches + d.expiredBatches, 'Lô cận / hết hạn', 'warning') + '</div>'
                + '<div class="row g-4"><div class="col-lg-6"><div class="card"><div class="card-header">Đơn thuốc chờ duyệt</div><ul class="list-group list-group-flush">'
                + (r[1].data.length ? r[1].data.map(function (p) {
                    return '<li class="list-group-item d-flex justify-content-between align-items-center"><div><strong>' + esc(p.customer) + '</strong><div class="small text-muted">'
                        + (p.orderCode ? 'Đơn ' + esc(p.orderCode) : 'Nhờ lên đơn') + ' · ' + dt(p.createdAt) + '</div></div><button class="btn btn-sm btn-outline-danger" data-reject="' + p.id + '">Từ chối</button></li>';
                }).join('') : '<li class="list-group-item text-muted">Không có.</li>') + '</ul></div></div>'
                + '<div class="col-lg-6"><div class="card"><div class="card-header">Đơn chờ xác nhận</div><ul class="list-group list-group-flush">'
                + (r[2].data.length ? r[2].data.map(function (o) {
                    return '<li class="list-group-item d-flex justify-content-between align-items-center"><div><strong>' + esc(o.code) + '</strong> · ' + money(o.total)
                        + '<div class="small text-muted">' + esc(o.recipient) + ' · ' + dt(o.createdAt) + '</div></div><button class="btn btn-sm btn-primary" data-confirm-order="' + o.id + '">Xác nhận</button></li>';
                }).join('') : '<li class="list-group-item text-muted">Không có.</li>') + '</ul></div></div></div>'
                + '<p class="small text-muted mt-3">Duyệt đơn thuốc đầy đủ (đối chiếu, ghi sổ thuốc kê đơn) thực hiện trên trang quản trị web.</p>');
            $view.querySelectorAll('[data-reject]').forEach(function (b) {
                b.addEventListener('click', function () {
                    var reason = prompt('Lý do từ chối (gửi cho khách):', 'Ảnh đơn thuốc mờ, không đọc được');
                    if (!reason) return;
                    api('POST', '/staff/prescriptions/' + b.dataset.reject + '/reject', { reason: reason }).then(function (j) { toast('success', j.message); pageStaff(); }, function (err) { toast('error', err.message); });
                });
            });
            $view.querySelectorAll('[data-confirm-order]').forEach(function (b) {
                b.addEventListener('click', function () {
                    api('POST', '/staff/orders/' + b.dataset.confirmOrder + '/status', { to: 'CONFIRMED', note: 'Xác nhận từ ứng dụng' }).then(function (j) { toast('success', j.message); pageStaff(); },
                        function (err) { toast('error', err.message + (err.errors.length ? ' ' + err.errors.join(' ') : '')); });
                });
            });
        }, fail);
    }

    /* ================= Màn hình: quản lý sản phẩm (admin, CRUD) ================= */
    function pageAdminProducts() {
        if (!requireLogin(['ADMIN'])) return;
        var q = query();
        skeleton(6);
        api('GET', '/admin/products?' + new URLSearchParams({ q: q.q || '', page: q.page || 1 }).toString()).then(function (j) {
            render('<div class="d-flex flex-wrap gap-2 justify-content-between mb-3"><h1 class="h4 mb-0">Quản lý sản phẩm</h1><a href="#/admin/products/new" class="btn btn-primary"><i class="bi bi-plus-lg"></i> Thêm sản phẩm</a></div>'
                + '<form id="s" class="d-flex gap-2 mb-3" style="max-width:420px"><input name="q" class="form-control" value="' + esc(q.q) + '" placeholder="Tên, hoạt chất, SĐK"><button class="btn btn-outline-primary">Tìm</button></form>'
                + '<div class="card"><div class="table-responsive"><table class="table align-middle mb-0"><thead><tr><th>Sản phẩm</th><th>Loại</th><th class="text-end">Giá</th><th class="text-end">Có thể bán</th><th></th></tr></thead><tbody>'
                + j.data.map(function (p) {
                    return '<tr><td>' + esc(p.name) + '<div class="small text-muted">' + esc(p.category || '') + '</div></td><td><span class="badge text-bg-light border">' + esc(p.drugType.label) + '</span></td>'
                        + '<td class="text-end">' + money(p.price) + '/' + esc(p.unit) + '</td><td class="text-end">' + p.available + '</td>'
                        + '<td class="text-nowrap"><a href="#/admin/products/' + p.id + '" class="btn btn-sm btn-outline-primary">Sửa</a> <button class="btn btn-sm btn-outline-danger" data-del="' + p.id + '" data-name="' + esc(p.name) + '" aria-label="Xóa"><i class="bi bi-trash"></i></button></td></tr>';
                }).join('') + '</tbody></table></div></div>' + pager(j.meta, '#/admin/products?q=' + encodeURIComponent(q.q || '') + '&'));
            document.getElementById('s').addEventListener('submit', function (e) { e.preventDefault(); location.hash = '#/admin/products?q=' + encodeURIComponent(e.target.q.value); });
            $view.querySelectorAll('[data-del]').forEach(function (b) {
                b.addEventListener('click', function () {
                    if (!confirm('Xóa sản phẩm "' + b.dataset.name + '"?')) return;
                    api('DELETE', '/admin/products/' + b.dataset.del).then(function (r) { toast('success', r.message); pageAdminProducts(); }, function (err) { toast('error', err.message); });
                });
            });
        }, fail);
    }

    function pageAdminProduct(id) {
        if (!requireLogin(['ADMIN'])) return;
        var isNew = id === 'new';
        skeleton(6);
        Promise.all([api('GET', '/categories'), isNew ? Promise.resolve(null) : api('GET', '/admin/products/' + id)]).then(function (r) {
            var cats = r[0].data, d = r[1] ? r[1].data : { product: { drugType: { code: 'OTC' }, unit: 'Hộp' }, active: true, units: [], minStock: 10, weightGram: 200 };
            var p = d.product;
            var units = d.units.concat([{}, {}, {}]).slice(0, 3);
            var input = function (name, label, value, type, col) {
                return '<div class="col-md-' + (col || 6) + '"><label class="form-label small">' + label + '</label><input name="' + name + '" type="' + (type || 'text') + '" class="form-control" value="' + esc(value == null ? '' : value) + '"></div>';
            };
            render('<a href="#/admin/products" class="small">← Danh sách</a><h1 class="h4 my-2">' + (isNew ? 'Thêm sản phẩm' : 'Sửa: ' + esc(p.name)) + '</h1>'
                + '<form id="pf" class="card card-body"><div class="form-errors"></div><div class="row g-3">'
                + input('name', 'Tên sản phẩm', p.name, 'text', 8)
                + '<div class="col-md-4"><label class="form-label small">Loại</label><select name="drugType" class="form-select">' + [['OTC', 'Không kê đơn'], ['ETC', 'Kê đơn'], ['SUPPLEMENT', 'TPCN'], ['DEVICE', 'Dụng cụ y tế'], ['COSMETIC', 'Dược mỹ phẩm']].map(function (t) {
                    return '<option value="' + t[0] + '"' + (p.drugType.code === t[0] ? ' selected' : '') + '>' + t[1] + '</option>';
                }).join('') + '</select></div>'
                + '<div class="col-md-6"><label class="form-label small">Danh mục</label><select name="categoryId" class="form-select"><option value="">--</option>' + cats.map(function (c) {
                    return '<option value="' + c.id + '"' + (d.categoryId === c.id ? ' selected' : '') + '>' + '— '.repeat(c.depth) + esc(c.name) + '</option>';
                }).join('') + '</select></div>'
                + input('activeIngredient', 'Hoạt chất', p.activeIngredient) + input('strength', 'Hàm lượng', p.strength, 'text', 4) + input('unit', 'Đơn vị gốc', p.unit, 'text', 4)
                + input('price', 'Giá bán (đ)', p.price, 'number', 4) + input('oldPrice', 'Giá gốc nếu giảm', p.oldPrice, 'number', 4) + input('minStock', 'Định mức tồn', d.minStock, 'number', 4)
                + input('registrationNo', 'Số đăng ký', d.registrationNo, 'text', 4)
                + '<div class="col-12 small fw-semibold">Đơn vị quy đổi</div>' + units.map(function (u, i) {
                    return '<div class="col-4"><input class="form-control form-control-sm" data-u="name" data-i="' + i + '" placeholder="Tên (VD: Hộp)" value="' + esc(u.name || '') + '"></div>'
                        + '<div class="col-4"><input type="number" class="form-control form-control-sm" data-u="factor" data-i="' + i + '" placeholder="Số đơn vị gốc" value="' + esc(u.factor || '') + '"></div>'
                        + '<div class="col-4"><input type="number" class="form-control form-control-sm" data-u="price" data-i="' + i + '" placeholder="Giá" value="' + esc(u.price || '') + '"></div>';
                }).join('')
                + '<div class="col-12"><label class="form-label small">Mô tả</label><textarea name="description" class="form-control" rows="3">' + esc(d.description || '') + '</textarea></div>'
                + '<div class="col-12 form-check ms-2"><input type="checkbox" class="form-check-input" id="act" name="active"' + (d.active ? ' checked' : '') + '><label class="form-check-label" for="act">Đang kinh doanh</label></div>'
                + '</div><button type="submit" class="btn btn-primary mt-3">Lưu</button></form>');
            var f = document.getElementById('pf');
            f.addEventListener('submit', function (e) {
                e.preventDefault();
                var body = formData(f);
                body.active = f.active.checked;
                body.units = [0, 1, 2].map(function (i) {
                    var g = function (k) { return f.querySelector('[data-u="' + k + '"][data-i="' + i + '"]').value; };
                    return { name: g('name'), factor: g('factor'), price: g('price') };
                }).filter(function (u) { return u.name; });
                submit(f, function () { return isNew ? api('POST', '/admin/products', body) : api('PUT', '/admin/products/' + id, body); }, function () { location.hash = '#/admin/products'; });
            });
        }, fail);
    }

    /* ---------------- Định tuyến ---------------- */
    var routes = [
        [/^#?\/?(\?.*)?$/, pageProducts],
        [/^#\/products\/([^?]+)/, pageProduct],
        [/^#\/cart/, pageCart],
        [/^#\/checkout/, pageCheckout],
        [/^#\/orders\/([^?]+)/, pageOrder],
        [/^#\/orders/, pageOrders],
        [/^#\/login/, pageLogin],
        [/^#\/register/, pageRegister],
        [/^#\/account/, pageAccount],
        [/^#\/consult/, pageConsult],
        [/^#\/staff/, pageStaff],
        [/^#\/admin\/products\/([^?]+)/, pageAdminProduct],
        [/^#\/admin\/products/, pageAdminProducts],
        [/^#\/logout/, function () {
            if (auth.token) api('POST', '/auth/logout').catch(function () { /* token hết hạn: vẫn đăng xuất phía client */ });
            saveAuth(null, null);
            toast('info', 'Bạn đã đăng xuất.');
            location.hash = '#/';
        }]
    ];

    function route() {
        clearInterval(chatTimer);
        var h = location.hash || '#/';
        for (var i = 0; i < routes.length; i++) {
            var m = h.match(routes[i][0]);
            if (m) return routes[i][1](m[1] && m[1].charAt(0) !== '?' ? decodeURIComponent(m[1]) : undefined);
        }
        render('<div class="alert alert-warning">Không tìm thấy màn hình.</div>');
    }

    window.addEventListener('hashchange', route);
    renderNav();
    route();
})();
