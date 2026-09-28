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

        var render = function (m) {
            var mine = String(m.senderId) === me;
            var wrap = document.createElement('div');
            wrap.className = 'chat-msg' + (mine ? ' mine' : '');
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
            var meta = document.createElement('div');
            meta.className = 'meta';
            meta.textContent = (mine ? '' : m.senderName + (m.fromStaff ? ' (Dược sĩ)' : '') + ' · ') + m.time;
            wrap.appendChild(bubble);
            wrap.appendChild(meta);
            var empty = box.querySelector('.chat-empty');
            if (empty) empty.remove();
            box.appendChild(wrap);
            box.scrollTop = box.scrollHeight;
        };

        var poll = function () {
            fetch(fetchUrl + '?after=' + lastId, {headers: {'Accept': 'application/json'}})
                .then(function (r) { return r.json(); })
                .then(function (data) {
                    (data.messages || []).forEach(function (m) {
                        if (m.id > lastId) { render(m); lastId = m.id; }
                    });
                })
                .catch(function () {});
        };

        box.scrollTop = box.scrollHeight;
        setInterval(poll, 4000);

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
