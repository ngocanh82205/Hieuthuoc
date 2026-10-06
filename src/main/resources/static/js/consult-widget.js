/**
 * VinaPharma – Floating Medicine Consultation Widget (Tư vấn thuốc)
 */
(function () {
    'use strict';

    var STORAGE_KEY = 'vinapharma_consult_widget_state';
    var widget = document.getElementById('consultWidget');
    if (!widget) return;

    var toggleBtn = document.getElementById('consultWidgetToggle');
    var panel = document.getElementById('consultWidgetPanel');
    var minimizeBtn = document.getElementById('consultWidgetMinimize');
    var maximizeBtn = document.getElementById('consultWidgetMaximize');
    var closeBtn = document.getElementById('consultWidgetClose');
    var inputBody = document.getElementById('consultInputBody');
    var chatBox = widget.querySelector('.chat-box');
    var imgInput = document.getElementById('consultImageInput');
    var previewWrap = document.getElementById('chatPreviewContainer');
    var previewImg = document.getElementById('chatPreview');
    var previewClear = document.getElementById('chatPreviewRemove');

    // Đọc trạng thái từ localStorage (mặc định 'minimized')
    function getStoredState() {
        try {
            var s = localStorage.getItem(STORAGE_KEY);
            if (s === 'normal' || s === 'expanded') return s;
        } catch (e) {}
        return 'minimized';
    }

    function setStoredState(s) {
        try {
            localStorage.setItem(STORAGE_KEY, s);
        } catch (e) {}
    }

    function scrollToBottom() {
        if (chatBox) {
            chatBox.scrollTop = chatBox.scrollHeight;
        }
    }

    function applyState(state, shouldFocus) {
        widget.setAttribute('data-state', state);
        setStoredState(state);

        var isOpen = state === 'normal' || state === 'expanded';
        if (toggleBtn) {
            toggleBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
            toggleBtn.setAttribute('title', isOpen ? 'Đóng tư vấn thuốc' : 'Mở tư vấn thuốc');
        }
        if (panel) {
            panel.setAttribute('aria-hidden', isOpen ? 'false' : 'true');
        }

        // Cập nhật icon phóng to / thu nhỏ
        if (maximizeBtn) {
            var iconMax = maximizeBtn.querySelector('.icon-maximize');
            var iconRestore = maximizeBtn.querySelector('.icon-restore');
            if (iconMax && iconRestore) {
                if (state === 'expanded') {
                    iconMax.classList.add('d-none');
                    iconRestore.classList.remove('d-none');
                    maximizeBtn.setAttribute('title', 'Thu nhỏ kích thước tiêu chuẩn');
                    maximizeBtn.setAttribute('aria-label', 'Thu nhỏ kích thước tiêu chuẩn');
                } else {
                    iconMax.classList.remove('d-none');
                    iconRestore.classList.add('d-none');
                    maximizeBtn.setAttribute('title', 'Phóng to khung tư vấn');
                    maximizeBtn.setAttribute('aria-label', 'Phóng to khung tư vấn');
                }
            }
        }

        if (isOpen) {
            setTimeout(function () {
                scrollToBottom();
                if (shouldFocus && inputBody) {
                    inputBody.focus();
                }
            }, 60);
        } else {
            // Khi đóng / thu nhỏ, nếu con trỏ đang ở trong widget thì trả về toggleBtn
            if (document.activeElement && panel && panel.contains(document.activeElement)) {
                if (toggleBtn) toggleBtn.focus();
            }
        }
    }

    // Toggle button ở góc phải dưới
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            var current = widget.getAttribute('data-state') || 'minimized';
            if (current === 'minimized') {
                applyState('normal', true);
            } else {
                applyState('minimized', false);
            }
        });
    }

    // Nút thu nhỏ (-)
    if (minimizeBtn) {
        minimizeBtn.addEventListener('click', function () {
            applyState('minimized', false);
        });
    }

    // Nút đóng (X)
    if (closeBtn) {
        closeBtn.addEventListener('click', function () {
            applyState('minimized', false);
        });
    }

    // Nút phóng to / thu lại
    if (maximizeBtn) {
        maximizeBtn.addEventListener('click', function () {
            var current = widget.getAttribute('data-state');
            if (current === 'expanded') {
                applyState('normal', true);
            } else {
                applyState('expanded', true);
            }
        });
    }

    // Phím Escape để đóng/thu nhỏ panel
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' || e.keyCode === 27) {
            var current = widget.getAttribute('data-state');
            if (current && current !== 'minimized') {
                applyState('minimized', false);
            }
        }
    });

    // Enter gửi tin nhắn, Shift+Enter xuống dòng
    if (inputBody) {
        inputBody.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                if (inputBody.readOnly || inputBody.disabled) return;
                var form = inputBody.closest('form');
                if (form) {
                    if (form.requestSubmit) {
                        form.requestSubmit();
                    } else {
                        form.dispatchEvent(new Event('submit', { cancelable: true, bubbles: true }));
                    }
                }
            }
        });

        // Tự co giãn chiều cao theo nội dung
        inputBody.addEventListener('input', function () {
            this.style.height = 'auto';
            var h = Math.min(this.scrollHeight, 120);
            this.style.height = (h > 36 ? h : 36) + 'px';
        });
    }

    // Xử lý ảnh đính kèm xem trước và xóa
    if (imgInput && previewImg && previewWrap) {
        imgInput.addEventListener('change', function () {
            var file = this.files && this.files[0];
            if (file) {
                previewImg.src = URL.createObjectURL(file);
                previewWrap.classList.remove('d-none');
            } else {
                previewWrap.classList.add('d-none');
            }
        });

        if (previewClear) {
            previewClear.addEventListener('click', function () {
                imgInput.value = '';
                previewImg.src = '';
                previewWrap.classList.add('d-none');
            });
        }
    }

    // Mở widget từ các nút/link khác trên trang (.js-open-consult-widget)
    document.addEventListener('click', function (e) {
        var link = e.target.closest('.js-open-consult-widget');
        if (link) {
            e.preventDefault();
            applyState('normal', true);
        }
    });

    // Khởi tạo trạng thái ban đầu
    var initialState = getStoredState();
    applyState(initialState, false);
})();
