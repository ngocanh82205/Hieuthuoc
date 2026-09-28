(function () {
    'use strict';

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var h = {'Accept': 'application/json'};
        if (token && header) h[header.content] = token.content;
        return h;
    }

    function toast(message, ok) {
        var box = document.getElementById('toastBox');
        if (!box || !window.bootstrap) { alert(message); return; }
        var el = document.createElement('div');
        el.className = 'toast align-items-center border-0 text-bg-' + (ok ? 'success' : 'danger');
        el.setAttribute('role', 'status');
        var wrap = document.createElement('div');
        wrap.className = 'd-flex';
        var body = document.createElement('div');
        body.className = 'toast-body';
        body.textContent = message;
        var btn = document.createElement('button');
        btn.className = 'btn-close btn-close-white me-2 m-auto';
        btn.setAttribute('data-bs-dismiss', 'toast');
        wrap.appendChild(body);
        wrap.appendChild(btn);
        el.appendChild(wrap);
        box.appendChild(el);
        new bootstrap.Toast(el, {delay: 3500}).show();
        el.addEventListener('hidden.bs.toast', function () { el.remove(); });
    }

    // Thêm vào giỏ hàng bằng AJAX (vẫn hoạt động bình thường nếu tắt JS)
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form.classList.contains('js-add-cart') || e.submitter && e.submitter.name === 'buyNow') return;
        e.preventDefault();
        fetch(form.action, {method: 'POST', headers: csrfHeaders(), body: new FormData(form)})
            .then(function (r) { return r.json(); })
            .then(function (data) {
                toast(data.message, data.ok);
                document.querySelectorAll('.js-cart-count').forEach(function (b) {
                    b.textContent = data.count;
                    b.classList.toggle('d-none', !data.count);
                });
            })
            .catch(function () { form.submit(); });
    });

    // Xác nhận trước khi submit
    document.addEventListener('submit', function (e) {
        var msg = (e.submitter && e.submitter.getAttribute('data-confirm')) || e.target.getAttribute('data-confirm');
        if (msg && !confirm(msg)) e.preventDefault();
    }, true);

    // Xem trước ảnh upload
    document.querySelectorAll('input[type=file][data-preview]').forEach(function (input) {
        input.addEventListener('change', function () {
            var img = document.getElementById(input.getAttribute('data-preview'));
            if (!img) return;
            if (input.files && input.files[0]) {
                img.src = URL.createObjectURL(input.files[0]);
                img.classList.remove('d-none');
            } else {
                img.classList.add('d-none');
            }
        });
    });

    // Chat tư vấn: polling tin nhắn mới mỗi 4 giây
    var chat = document.getElementById('chat');
    if (chat) {
        var box = chat.querySelector('.chat-box');
        var form = chat.querySelector('form');
        var me = chat.getAttribute('data-me');
        var lastId = parseInt(chat.getAttribute('data-last-id') || '0', 10);
        var fetchUrl = chat.getAttribute('data-fetch-url');

        var typing = document.getElementById('typing');
        var waitingSince = 0;
        var setTyping = function (on) {
            if (!typing) return;
            waitingSince = on ? Date.now() : 0;
            typing.classList.toggle('d-none', !on);
            if (on) { box.appendChild(typing); box.scrollTop = box.scrollHeight; }
        };
        var render = function (m) {
            var mine = String(m.senderId) === me;
            var empty0 = box.querySelector('.chat-empty');
            if (m.kind === 'SYSTEM') {
                if (empty0) empty0.remove();
                var sys = document.createElement('div');
                sys.className = 'chat-system';
                sys.textContent = m.body;
                box.appendChild(sys);
                box.scrollTop = box.scrollHeight;
                return;
            }
            if (!mine) setTyping(false);
            var wrap = document.createElement('div');
            wrap.className = 'chat-msg' + (mine ? ' mine' : (m.kind === 'AI' ? ' ai' : ''));
            var bubble = document.createElement('div');
            bubble.className = 'bubble';
            if (m.body) bubble.appendChild(document.createTextNode(m.body));
            if (m.imageUrl) {
                var a = document.createElement('a');
                a.href = m.imageUrl;
                a.target = '_blank';
                var img = document.createElement('img');
                img.src = m.imageUrl;
                img.alt = 'Ảnh đính kèm';
                a.appendChild(img);
                bubble.appendChild(a);
            }
            if (m.cartId) {
                var cartBox = document.createElement('div');
                cartBox.className = 'chat-cart';
                (m.cartLines || []).forEach(function (l) {
                    var d = document.createElement('div');
                    d.textContent = '• ' + l;
                    cartBox.appendChild(d);
                });
                var tot = document.createElement('div');
                tot.className = 'fw-semibold mt-1';
                tot.textContent = 'Tạm tính: ' + Number(m.cartTotal).toLocaleString('vi-VN') + ' ₫';
                cartBox.appendChild(tot);
                var action = chat.getAttribute('data-cart-action');
                if (action) {
                    var f = document.createElement('form');
                    f.method = 'post';
                    f.action = action + m.cartId + '/add';
                    f.className = 'mt-2';
                    var tk = document.querySelector('meta[name="_csrf"]');
                    if (tk) {
                        var hid = document.createElement('input');
                        hid.type = 'hidden';
                        hid.name = '_csrf';
                        hid.value = tk.content;
                        f.appendChild(hid);
                    }
                    var b = document.createElement('button');
                    b.className = 'btn btn-sm btn-light';
                    b.textContent = 'Thêm tất cả vào giỏ';
                    f.appendChild(b);
                    cartBox.appendChild(f);
                }
                bubble.appendChild(cartBox);
            }
            var meta = document.createElement('div');
            meta.className = 'meta';
            meta.textContent = (mine ? '' : (m.kind === 'AI' ? '🤖 ' : '') + m.senderName + (m.fromStaff ? ' (Dược sĩ)' : '') + ' · ') + m.time;
            wrap.appendChild(bubble);
            wrap.appendChild(meta);
            var empty = box.querySelector('.chat-empty');
            if (empty) empty.remove();
            if (typing && !typing.classList.contains('d-none')) box.insertBefore(wrap, typing);
            else box.appendChild(wrap);
            box.scrollTop = box.scrollHeight;
        };

        // Chế độ trợ lý AI / dược sĩ: cập nhật tiêu đề khi được chuyển
        var handoffBtn = document.getElementById('handoffBtn');
        var setMode = function (mode, pharmacist) {
            if (!mode || chat.getAttribute('data-mode') === mode && !pharmacist) return;
            chat.setAttribute('data-mode', mode);
            var ai = mode === 'AI';
            var header = document.getElementById('chatHeader');
            if (header) {
                header.querySelector('[data-title]').textContent = ai ? chat.getAttribute('data-ai-name') : (pharmacist || 'Dược sĩ VinaPharma');
                header.querySelector('[data-subtitle]').textContent = ai ? 'Trợ lý tự động · trả lời ngay' : (pharmacist ? 'Dược sĩ phụ trách' : 'Đang chờ dược sĩ tiếp nhận');
                var av = header.querySelector('.chat-avatar');
                av.classList.toggle('ai', ai);
                av.innerHTML = ai ? '<i class="bi bi-robot"></i>' : '<i class="bi bi-person-badge"></i>';
            }
            if (handoffBtn) handoffBtn.classList.toggle('d-none', !ai);
            var note = document.getElementById('aiNote');
            if (note) note.classList.toggle('d-none', !ai);
            if (!ai) setTyping(false);
        };

        var poll = function () {
            fetch(fetchUrl + '?after=' + lastId, {headers: {'Accept': 'application/json'}})
                .then(function (r) { return r.json(); })
                .then(function (data) {
                    (data.messages || []).forEach(function (m) {
                        if (m.id > lastId) { render(m); lastId = m.id; }
                    });
                    if (data.mode) setMode(data.mode, data.pharmacist);
                })
                .catch(function () {});
        };

        box.scrollTop = box.scrollHeight;
        // Đang chờ trợ lý trả lời: hỏi nhanh hơn (1,5 giây), tối đa 60 giây
        setInterval(function () {
            if (waitingSince && Date.now() - waitingSince > 60000) setTyping(false);
            if (waitingSince) poll();
        }, 1500);
        setInterval(function () { if (!waitingSince) poll(); }, 4000);

        if (handoffBtn) handoffBtn.addEventListener('click', function () {
            handoffBtn.disabled = true;
            fetch(chat.getAttribute('data-handoff-url'), {method: 'POST', headers: csrfHeaders()})
                .then(function () { poll(); })
                .finally(function () { handoffBtn.disabled = false; });
        });
        chat.querySelectorAll('.quick-reply').forEach(function (b) {
            b.addEventListener('click', function () {
                form.querySelector('textarea[name=body]').value = b.getAttribute('data-quick');
                form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit', {cancelable: true}));
            });
        });

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var fd = new FormData(form);
            var text = (fd.get('body') || '').trim();
            var file = fd.get('image');
            if (!text && !(file && file.size)) return;
            var btn = form.querySelector('button[type=submit]');
            btn.disabled = true;
            fetch(form.action, {method: 'POST', headers: csrfHeaders(), body: fd})
                .then(function (r) { return r.json(); })
                .then(function (data) {
                    if (data.ok === false) { toast(data.message, false); return; }
                    if (data.mode) setMode(data.mode);
                    if (data.mode === 'AI') setTyping(true);
                    form.reset();
                    var prev = form.querySelector('img[id]');
                    if (prev) prev.classList.add('d-none');
                    poll();
                })
                .catch(function () { toast('Không gửi được tin nhắn.', false); })
                .finally(function () { btn.disabled = false; });
        });

        form.querySelectorAll('[data-insert]').forEach(function (el) {
            el.addEventListener('change', function () {
                if (!el.value) return;
                var ta = form.querySelector('textarea');
                ta.value = (ta.value ? ta.value + '\n' : '') + el.value;
                el.value = '';
                ta.focus();
            });
        });
    }

    // Phiếu nhập: thêm dòng sản phẩm
    var addRow = document.getElementById('addReceiptRow');
    if (addRow) {
        addRow.addEventListener('click', function () {
            var tbody = document.getElementById('receiptRows');
            var tpl = document.getElementById('receiptRowTemplate');
            var index = tbody.querySelectorAll('tr').length;
            var html = tpl.innerHTML.replace(/__i__/g, String(index));
            tbody.insertAdjacentHTML('beforeend', html);
        });
        document.addEventListener('click', function (e) {
            var btn = e.target.closest('.js-remove-row');
            if (btn && document.querySelectorAll('#receiptRows tr').length > 1) {
                var row = btn.closest('tr');
                row.querySelector('select').value = '';
                row.classList.add('d-none');
            }
        });
    }

    // Tự gửi form khi đổi bộ lọc
    document.querySelectorAll('[data-autosubmit]').forEach(function (el) {
        el.addEventListener('change', function () { el.form.submit(); });
    });
})();

/* ===== Hiệu ứng giao diện ===== */
(function () {
    'use strict';
    // Header đổ bóng khi cuộn + nút lên đầu trang
    var header = document.querySelector('.site-header');
    var toTop = document.getElementById('backToTop');
    var onScroll = function () {
        var y = window.scrollY;
        if (header) header.classList.toggle('scrolled', y > 10);
        if (toTop) toTop.classList.toggle('show', y > 500);
    };
    window.addEventListener('scroll', onScroll, {passive: true});
    onScroll();
    if (toTop) toTop.addEventListener('click', function () { window.scrollTo({top: 0, behavior: 'smooth'}); });

    // Hiện dần các khối khi cuộn tới
    var items = document.querySelectorAll('.reveal, .reveal-stagger');
    if ('IntersectionObserver' in window) {
        var io = new IntersectionObserver(function (entries) {
            entries.forEach(function (e) {
                if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
            });
        }, {threshold: 0.12, rootMargin: '0px 0px -40px 0px'});
        items.forEach(function (el) { io.observe(el); });
    } else {
        items.forEach(function (el) { el.classList.add('in'); });
    }

    // Đồng hồ đếm ngược flash sale (đến hết ngày)
    var cd = document.getElementById('countdown');
    if (cd) {
        var pad = function (n) { return String(n).padStart(2, '0'); };
        var tick = function () {
            var now = new Date();
            var end = cd.dataset.end ? new Date(cd.dataset.end) : new Date(now);
            if (!cd.dataset.end) end.setHours(23, 59, 59, 999);
            var s = Math.max(0, Math.floor((end - now) / 1000));
            cd.querySelector('[data-h]').textContent = pad(Math.floor(s / 3600));
            cd.querySelector('[data-m]').textContent = pad(Math.floor(s % 3600 / 60));
            cd.querySelector('[data-s]').textContent = pad(s % 60);
        };
        tick();
        setInterval(tick, 1000);
    }

    // Đếm ngược flash sale trên trang sản phẩm
    document.querySelectorAll('[data-countdown]').forEach(function (el) {
        var end = new Date(el.dataset.end);
        var pad = function (n) { return String(n).padStart(2, '0'); };
        var tick = function () {
            var s = Math.max(0, Math.floor((end - new Date()) / 1000));
            var d = Math.floor(s / 86400);
            el.textContent = (d > 0 ? d + ' ngày ' : '') + pad(Math.floor(s % 86400 / 3600)) + ':' + pad(Math.floor(s % 3600 / 60)) + ':' + pad(s % 60);
        };
        tick();
        setInterval(tick, 1000);
    });

    // Hiệu ứng "nảy" biểu tượng giỏ hàng khi thêm sản phẩm
    document.addEventListener('submit', function (e) {
        if (!e.target.classList.contains('js-add-cart')) return;
        var bag = document.querySelector('.bi-bag-heart');
        if (bag && bag.animate) bag.animate([{transform: 'scale(1)'}, {transform: 'scale(1.35) rotate(-10deg)'}, {transform: 'scale(1)'}], {duration: 500, delay: 300});
    });
})();

/* ===== Yêu thích (AJAX) & thông báo mới (toast) ===== */
(function () {
    'use strict';
    var headers = function () {
        var t = document.querySelector('meta[name="_csrf"]'), h = document.querySelector('meta[name="_csrf_header"]');
        var o = {'Accept': 'application/json'};
        if (t && h) o[h.content] = t.content;
        return o;
    };
    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form.classList.contains('js-wish')) return;
        if (!document.querySelector('meta[name="logged-in"]')) return; // chưa đăng nhập -> submit thường để chuyển tới trang đăng nhập
        e.preventDefault();
        fetch(form.action, {method: 'POST', headers: headers(), body: new FormData(form)})
            .then(function (r) { if (!r.ok) throw r; return r.json(); })
            .then(function (d) {
                var btn = form.querySelector('.wish-btn'), icon = btn.querySelector('i');
                btn.classList.toggle('on', d.added);
                icon.className = 'bi ' + (d.added ? 'bi-heart-fill' : 'bi-heart');
            })
            .catch(function () { form.submit(); });
    });

    // Kiểm tra thông báo mới mỗi 30 giây (VD: nhắc giờ uống thuốc, đơn thuốc đã duyệt)
    if (!document.querySelector('meta[name="logged-in"]')) return;
    var seen = {};
    var first = true;
    var box = document.getElementById('toastBox');
    var check = function () {
        fetch('/notifications/unread', {headers: {'Accept': 'application/json'}})
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (d) {
                if (!d) return;
                document.querySelectorAll('.bi-bell').forEach(function (bell) {
                    var holder = bell.parentElement, badge = holder.querySelector('.badge');
                    if (d.count > 0) {
                        if (!badge) {
                            badge = document.createElement('span');
                            badge.className = 'badge rounded-pill bg-danger position-absolute top-0 start-100 translate-middle';
                            holder.appendChild(badge);
                        }
                        badge.textContent = d.count;
                    } else if (badge) badge.remove();
                });
                (d.latest || []).forEach(function (n) {
                    if (seen[n.id]) return;
                    seen[n.id] = true;
                    if (first || !box || !window.bootstrap) return;
                    var el = document.createElement('div');
                    el.className = 'toast border-0';
                    var body = document.createElement('a');
                    body.className = 'toast-body d-block text-reset';
                    body.href = n.link || '/notifications';
                    var icon = document.createElement('i');
                    icon.className = 'bi bi-bell-fill text-primary me-2';
                    body.appendChild(icon);
                    body.appendChild(document.createTextNode(n.message));
                    el.appendChild(body);
                    box.appendChild(el);
                    new bootstrap.Toast(el, {delay: 8000}).show();
                });
                first = false;
            })
            .catch(function () {});
    };
    check();
    setInterval(check, 30000);
})();
