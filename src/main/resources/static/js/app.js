function baseUrl() {
    var m = document.querySelector('meta[name="base-url"]');
    return m ? m.content.replace(/\/$/, '') : '';
}

(function () {
    'use strict';

    // Bật/tắt ẩn hiện mật khẩu (con mắt)
    window.togglePassword = function (btn) {
        if (!btn) return;
        var group = btn.closest('.input-group');
        var input = group ? group.querySelector('input') : null;
        if (!input) return;
        var icon = btn.querySelector('i');
        if (input.type === 'password') {
            input.type = 'text';
            if (icon) {
                icon.className = 'bi bi-eye-slash';
            }
            btn.setAttribute('title', 'Ẩn mật khẩu');
        } else {
            input.type = 'password';
            if (icon) {
                icon.className = 'bi bi-eye';
            }
            btn.setAttribute('title', 'Hiện mật khẩu');
        }
    };

    document.addEventListener('click', function (e) {
        var btn = e.target.closest('.toggle-password-btn');
        if (btn) {
            window.togglePassword(btn);
        }
    });

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

    // Chat tư vấn: chỉ khởi tạo khi đã đăng nhập và có đầy đủ thành phần giao diện
    function initConsultChat() {
        var chat = document.getElementById('chat');
        if (!chat) return;

        var box = chat.querySelector('.chat-box');
        var form = chat.querySelector('form');
        var me = (chat.getAttribute('data-me') || '').trim();
        var fetchUrl = chat.getAttribute('data-fetch-url');

        // Chỉ khởi tạo logic chat khi tồn tại đầy đủ:
        // #chat, .chat-box, form, data-me có giá trị (khách hàng đã đăng nhập).
        // Nếu thiếu form hoặc thiếu data-me (khách chưa đăng nhập / tài khoản nhân viên):
        // widget chỉ hiển thị thông báo tĩnh/nút đăng nhập, không gọi API chat và không polling.
        if (!box || !form || !me || !fetchUrl) {
            return;
        }

        var lastId = parseInt(chat.getAttribute('data-last-id') || '0', 10);

        // Tập hợp các ID tin nhắn đã hiển thị để chống duplicate tuyệt đối
        var renderedIds = new Set();
        box.querySelectorAll('.chat-msg[data-id]').forEach(function (el) {
            var mid = parseInt(el.getAttribute('data-id'), 10);
            if (mid) renderedIds.add(mid);
        });

        var typing = document.getElementById('typing');
        var waitingSince = 0;
        var setTyping = function (on) {
            if (!typing) return;
            waitingSince = on ? Date.now() : 0;
            typing.classList.toggle('d-none', !on);
            typing.setAttribute('aria-busy', on ? 'true' : 'false');
            typing.setAttribute('aria-live', 'polite');
            if (on) {
                box.appendChild(typing);
                box.scrollTop = box.scrollHeight;
            }
        };

        var render = function (m) {
            if (!m) return;
            if (m.id && renderedIds.has(m.id)) return;
            if (m.id) renderedIds.add(m.id);

            var mine = String(m.senderId) === me || m.kind === 'USER';
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

            var wrap = document.createElement('div');
            wrap.className = 'chat-msg' + (mine ? ' mine' : (m.kind === 'AI' ? ' ai' : ''));
            if (m.id) wrap.setAttribute('data-id', m.id);

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
                        hid.name = '_token';
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

            // Nếu typing bubble đang bật, render tin nhắn phía trên typing bubble để không bị giật vị trí
            if (typing && !typing.classList.contains('d-none')) {
                box.insertBefore(wrap, typing);
            } else {
                box.appendChild(wrap);
            }

            // Khi nhận được tin nhắn từ AI hoặc dược sĩ (!mine), tắt typing sau khi đã hiển thị tin nhắn
            if (!mine) {
                setTyping(false);
            }

            box.scrollTop = box.scrollHeight;
        };

        // Chế độ trợ lý AI / dược sĩ: cập nhật tiêu đề khi được chuyển
        var handoffBtn = document.getElementById('handoffBtn');
        var resumeAiBtn = document.getElementById('resumeAiBtn');
        var setMode = function (mode, pharmacist) {
            if (!mode) return;
            var currentMode = chat.getAttribute('data-mode');
            if (currentMode === mode && !pharmacist) return;
            chat.setAttribute('data-mode', mode);
            var ai = mode === 'AI';
            var header = document.getElementById('chatHeader');
            if (header) {
                var titleEl = header.querySelector('[data-title]');
                var subEl = header.querySelector('[data-subtitle]');
                if (titleEl) titleEl.textContent = ai ? chat.getAttribute('data-ai-name') : (pharmacist || 'Dược sĩ VinaPharma');
                if (subEl) subEl.textContent = ai ? 'Trợ lý tự động · trả lời ngay' : (pharmacist ? 'Dược sĩ phụ trách' : 'Đang chờ dược sĩ phản hồi');
                var av = header.querySelector('.chat-avatar');
                if (av) {
                    av.classList.toggle('ai', ai);
                    av.innerHTML = ai ? '<i class="bi bi-robot"></i>' : '<i class="bi bi-person-badge"></i>';
                }
            }
            if (handoffBtn) handoffBtn.classList.toggle('d-none', !ai);
            if (resumeAiBtn) resumeAiBtn.classList.toggle('d-none', ai);
            var note = document.getElementById('aiNote');
            if (note) note.classList.toggle('d-none', !ai);
            if (!ai) setTyping(false);
        };

        var poll = function () {
            fetch(fetchUrl + '?after=' + lastId, {headers: {'Accept': 'application/json'}})
                .then(function (r) { return r.json(); })
                .then(function (data) {
                    (data.messages || []).forEach(function (m) {
                        if (m.id > lastId) {
                            lastId = m.id;
                        }
                        if (!renderedIds.has(m.id)) {
                            render(m);
                        }
                    });
                    if (data.mode) setMode(data.mode, data.pharmacist);
                })
                .catch(function () {});
        };

        box.scrollTop = box.scrollHeight;
        // Đang chờ trợ lý trả lời: hỏi nhanh hơn (1,5 giây), tối đa 60 giây
        setInterval(function () {
            if (waitingSince && Date.now() - waitingSince > 60000) {
                setTyping(false);
                toast('Trợ lý AI đang phản hồi chậm, vui lòng chờ trong giây lát hoặc gửi lại câu hỏi.', false);
            }
            if (waitingSince) poll();
        }, 1500);
        setInterval(function () { if (!waitingSince) poll(); }, 3000);

        if (handoffBtn) handoffBtn.addEventListener('click', function () {
            var url = chat.getAttribute('data-handoff-url');
            if (!url) return;
            handoffBtn.disabled = true;
            fetch(url, {method: 'POST', headers: csrfHeaders()})
                .then(function (r) {
                    return r.json().then(function (data) {
                        return { status: r.status, ok: r.ok, data: data };
                    }).catch(function () {
                        return { status: r.status, ok: r.ok, data: {} };
                    });
                })
                .then(function (res) {
                    if (!res.ok || res.data.ok === false) {
                        toast(res.data.message || 'Không thể kết nối dược sĩ lúc này.', false);
                        return;
                    }
                    setMode('HUMAN');
                    poll();
                })
                .catch(function () {
                    toast('Lỗi kết nối máy chủ khi chuyển dược sĩ.', false);
                })
                .finally(function () {
                    handoffBtn.disabled = false;
                });
        });

        if (resumeAiBtn) resumeAiBtn.addEventListener('click', function () {
            var url = chat.getAttribute('data-resume-ai-url');
            if (!url) return;
            resumeAiBtn.disabled = true;
            fetch(url, {method: 'POST', headers: csrfHeaders()})
                .then(function (r) {
                    return r.json().then(function (data) {
                        return { status: r.status, ok: r.ok, data: data };
                    }).catch(function () {
                        return { status: r.status, ok: r.ok, data: {} };
                    });
                })
                .then(function (res) {
                    if (!res.ok || res.data.ok === false) {
                        toast(res.data.message || 'Không thể chuyển sang Trợ lý AI lúc này.', false);
                        return;
                    }
                    setMode('AI');
                    poll();
                })
                .catch(function () {
                    toast('Lỗi kết nối máy chủ khi quay lại Trợ lý AI.', false);
                })
                .finally(function () {
                    resumeAiBtn.disabled = false;
                });
        });
        chat.querySelectorAll('.quick-reply').forEach(function (b) {
            b.addEventListener('click', function () {
                var ta = form.querySelector('textarea[name=body]');
                if (ta) ta.value = b.getAttribute('data-quick');
                form.requestSubmit ? form.requestSubmit() : form.dispatchEvent(new Event('submit', {cancelable: true}));
            });
        });

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var fd = new FormData(form);
            var text = (fd.get('body') || '').trim();
            var file = fd.get('image');
            if (!text && !(file && file.size)) return;

            var ta = form.querySelector('textarea[name=body]');
            var fileInput = form.querySelector('input[type=file]');
            var btn = form.querySelector('button[type=submit]');
            var prev = form.querySelector('img[id]');
            var prevWrap = document.getElementById('consultImgPreviewWrap');

            // 1. Khóa tạm thời ô nhập và nút gửi trong lúc request đang xử lý
            if (ta) ta.readOnly = true;
            if (fileInput) fileInput.disabled = true;
            if (btn) btn.disabled = true;

            var unlockForm = function () {
                if (ta) ta.readOnly = false;
                if (fileInput) fileInput.disabled = false;
                if (btn) btn.disabled = false;
            };

            fetch(form.action, {method: 'POST', headers: csrfHeaders(), body: fd})
                .then(function (r) {
                    if (!r.ok) {
                        return r.json().catch(function () {
                            return { ok: false, message: 'Lỗi kết nối máy chủ (' + r.status + ')' };
                        });
                    }
                    return r.json();
                })
                .then(function (data) {
                    if (data.ok === false) {
                        unlockForm();
                        toast(data.message || 'Không gửi được tin nhắn.', false);
                        return;
                    }

                    // 2. Server xác nhận lưu thành công: render ngay tin nhắn user
                    if (data.message) {
                        render(data.message);
                        if (data.message.id && data.message.id > lastId) {
                            lastId = data.message.id;
                        }
                    }

                    // 3. Xóa nội dung form và reset kích thước ô nhập
                    form.reset();
                    if (ta) ta.style.height = '36px';
                    if (prev) prev.src = '';
                    if (prevWrap) prevWrap.classList.add('d-none');
                    unlockForm();

                    // 4. Cập nhật mode và hiển thị bubble suy nghĩ nếu là AI
                    if (data.mode) setMode(data.mode);
                    var currentMode = chat.getAttribute('data-mode') || data.mode;
                    if (currentMode === 'AI') {
                        setTyping(true);
                    }

                    // 5. Bắt đầu poll ngay để đón câu trả lời
                    poll();
                })
                .catch(function () {
                    unlockForm();
                    toast('Không gửi được tin nhắn. Vui lòng kiểm tra kết nối và thử lại.', false);
                });
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

    try {
        initConsultChat();
    } catch (e) {
        console.error('Lỗi khởi tạo chat tư vấn:', e);
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
                row.querySelectorAll('.js-product-id, .js-product-search').forEach(function (el) { el.value = ''; });
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

    // Hiện dần các khối khi cuộn tới (độc lập, luôn hiển thị sản phẩm kể cả khi có lỗi script khác)
    try {
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
    } catch (e) {
        document.querySelectorAll('.reveal, .reveal-stagger').forEach(function (el) { el.classList.add('in'); });
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
        fetch(baseUrl() + '/notifications/unread', {headers: {'Accept': 'application/json'}})
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
                    body.href = n.link ? (n.link.charAt(0) === '/' ? baseUrl() + n.link : n.link) : baseUrl() + '/notifications';
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



/* ===== Chia sẻ mạng xã hội ===== */
(function () {
    'use strict';
    document.addEventListener('click', function (e) {
        var a = e.target.closest('.js-share');
        if (!a) return;
        if (a.classList.contains('js-copy-link')) {
            var url = a.getAttribute('data-url');
            if (navigator.share) { navigator.share({url: url}).catch(function () {}); return; }
            if (navigator.clipboard) navigator.clipboard.writeText(url);
            a.innerHTML = '<i class="bi bi-check2 text-success"></i>';
            setTimeout(function () { a.innerHTML = '<i class="bi bi-link-45deg"></i>'; }, 1500);
        }
    });

    // Gợi ý tìm kiếm
    var input = document.querySelector('.search-form input[name=q]');
    if (input) {
        var list = document.createElement('datalist');
        list.id = 'searchSuggest';
        document.body.appendChild(list);
        var timer;
        input.addEventListener('input', function () {
            clearTimeout(timer);
            var q = input.value.trim();
            if (q.length < 2) return;
            timer = setTimeout(function () {
                fetch(baseUrl() + '/products/suggest?q=' + encodeURIComponent(q), {headers: {'Accept': 'application/json'}})
                    .then(function (r) { return r.json(); })
                    .then(function (items) {
                        list.innerHTML = '';
                        items.forEach(function (it) {
                            var o = document.createElement('option');
                            o.value = it.name;
                            o.label = it.price;
                            list.appendChild(o);
                        });
                    }).catch(function () {});
            }, 250);
        });
    }
})();
