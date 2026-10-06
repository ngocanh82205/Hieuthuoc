// Trang thanh toán: chọn địa chỉ (sổ địa chỉ / GHN / bản đồ Leaflet), tính phí giao hàng (chuyển từ bản Laravel).
(function () {
    var ghn = (document.getElementById('checkoutForm') || {dataset: {}}).dataset.ghn === 'true';
    var base = document.querySelector('meta[name="base-url"]').content.replace(/\/$/, '');
    var $ = function (id) { return document.getElementById(id); };
    var pickup = function () { var c = document.querySelector('input[name=shipping_method]:checked'); return c && c.value === 'PICKUP'; };
    var timer;
    var recalc = function () {
        clearTimeout(timer);
        timer = setTimeout(function () {
            var q = new URLSearchParams({shipping_method: pickup() ? 'PICKUP' : 'DELIVERY', province: $('province').value || ''});
            if (ghn) { q.set('district_id', $('ghnDistrict').value || ''); q.set('ward_code', $('ghnWard').value || ''); }
            fetch(base + '/shipping/fee?' + q.toString(), {headers: {'Accept': 'application/json'}})
                .then(function (r) { return r.json(); })
                .then(function (d) { $('shipFee').textContent = d.fee_text; $('grandTotal').textContent = d.total_text; })
                .catch(function () {});
        }, 150);
    };
    var fill = function (sel, items, valueKey, selected, placeholder) {
        sel.innerHTML = '<option value="">' + placeholder + '</option>';
        items.forEach(function (it) {
            var o = document.createElement('option');
            o.value = it[valueKey]; o.textContent = it.name;
            if (String(it[valueKey]) === String(selected || '')) o.selected = true;
            sel.appendChild(o);
        });
    };
    // Mỗi ô chỉ nhận kết quả của lần tải mới nhất (tránh phản hồi cũ về muộn ghi đè lựa chọn mới)
    var load = function (url, sel, key, selected, placeholder) {
        var seq = sel.dataset.seq = String((+sel.dataset.seq || 0) + 1);
        return fetch(base + url, {headers: {'Accept': 'application/json'}}).then(function (r) { return r.json(); })
            .then(function (items) { if (sel.dataset.seq === seq) fill(sel, items, key, selected, placeholder); });
    };

    var prov, dist, ward;
    var onProvince, onDistrict;

    if (ghn) {
        prov = $('ghnProvince'); dist = $('ghnDistrict'); ward = $('ghnWard');
        onProvince = function (keep) {
            $('province').value = prov.value ? prov.selectedOptions[0].textContent : '';
            ward.dataset.seq = String((+ward.dataset.seq || 0) + 1);
            fill(ward, [], 'code', null, '-- Chọn --');
            if (!prov.value) { fill(dist, [], 'id', null, '-- Chọn --'); recalc(); return Promise.resolve(); }
            return load('/shipping/districts?province_id=' + prov.value, dist, 'id', keep ? dist.dataset.selected : null, '-- Chọn quận/huyện --');
        };
        onDistrict = function (keep) {
            if (!dist.value) { fill(ward, [], 'code', null, '-- Chọn --'); recalc(); return Promise.resolve(); }
            return load('/shipping/wards?district_id=' + dist.value, ward, 'code', keep ? ward.dataset.selected : null, '-- Chọn phường/xã --');
        };
        load('/shipping/provinces', prov, 'id', prov.dataset.selected, '-- Chọn tỉnh/thành --')
            .then(function () { if (prov.value) return onProvince(true).then(function () { return onDistrict(true); }); })
            .then(recalc);
        // Khách tự chọn lại khu vực: thông báo nhận diện cũ không còn đúng
        var userPicked = function (e) { if (e.isTrusted) hideAddressAlert(); };
        prov.addEventListener('change', function (e) { userPicked(e); onProvince(false); });
        dist.addEventListener('change', function (e) { userPicked(e); onDistrict(false).then(recalc); });
        ward.addEventListener('change', function (e) { userPicked(e); recalc(); });
    } else {
        $('province').addEventListener('change', recalc);
    }

    var picker = $('addressPicker');
    var btnDelAddr = $('btnDeleteSelectedAddress');
    if (picker) {
        picker.addEventListener('change', function () {
            var o = picker.selectedOptions[0];
            var addrId = o ? o.value : '';
            if (btnDelAddr) {
                btnDelAddr.classList.toggle('d-none', !addrId);
            }
            if (!o || !o.dataset.address) return;
            $('recipient').value = o.dataset.recipient;
            $('phone').value = o.dataset.phone;
            $('address').value = o.dataset.address;
            if (ghn) {
                hideAddressAlert();
                prov.value = o.dataset.ghnProvince || '';
                dist.dataset.selected = o.dataset.ghnDistrict || '';
                ward.dataset.selected = o.dataset.ghnWard || '';
                // Giữ đúng quận / phường của địa chỉ đã lưu
                onProvince(true).then(function () { return onDistrict(true); }).then(recalc);
            } else {
                $('province').value = o.dataset.province || '';
                recalc();
            }
        });

        if (btnDelAddr) {
            btnDelAddr.addEventListener('click', function () {
                var o = picker.selectedOptions[0];
                var addrId = o ? o.value : '';
                if (!addrId) return;

                if (!confirm('Bạn có chắc chắn muốn xóa địa chỉ này khỏi sổ địa chỉ đã lưu không?')) {
                    return;
                }

                btnDelAddr.disabled = true;
                var csrf = document.querySelector('input[name=_token]') ? document.querySelector('input[name=_token]').value : '';

                fetch(base + '/account/addresses/' + addrId + '/delete', {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json',
                        'X-CSRF-TOKEN': csrf,
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: JSON.stringify({ _token: csrf })
                })
                .then(function (r) { return r.json(); })
                .then(function (res) {
                    btnDelAddr.disabled = false;
                    if (res && res.ok) {
                        o.remove();
                        picker.value = '';
                        btnDelAddr.classList.add('d-none');
                        showAddressAlert('Đã xóa địa chỉ khỏi sổ địa chỉ thành công.', true);

                        if (picker.options.length <= 1) {
                            var block = $('addressPickerBlock');
                            if (block) block.classList.add('d-none');
                        }
                    } else {
                        alert((res && res.message) ? res.message : 'Không thể xóa địa chỉ.');
                    }
                })
                .catch(function (err) {
                    btnDelAddr.disabled = false;
                    console.error('Delete address error', err);
                    alert('Lỗi kết nối khi xóa địa chỉ. Vui lòng thử lại.');
                });
            });
        }
    }

    var sync = function () {
        var p = pickup();
        $('addressBlock').classList.toggle('d-none', p);
        $('pickupBlock').classList.toggle('d-none', !p);
        $('address').required = !p;
        document.querySelectorAll('.addr-part').forEach(function (e) { e.classList.toggle('d-none', p); });
        if (!ghn) $('province').required = !p;
        recalc();
    };
    document.querySelectorAll('input[name=shipping_method]').forEach(function (r) { r.addEventListener('change', sync); });
    var vat = $('requestVat');
    var syncVat = function () { $('vatBlock').classList.toggle('d-none', !vat.checked); };
    vat.addEventListener('change', syncVat);
    syncVat();
    sync();

    /* =========================================================================
       BẢN ĐỒ TƯƠNG TÁC LEAFLET & TỰ ĐỘNG KHỚP TỈNH / QUẬN / PHƯỜNG / ĐỊA CHỈ
       ========================================================================= */

    function removeVietnameseTones(str) {
        if (!str) return '';
        str = str.toLowerCase();
        str = str.replace(/à|á|ạ|ả|ã|â|ầ|ấ|ậ|ẩ|ẫ|ă|ằ|ắ|ặ|ẳ|ẵ/g, "a");
        str = str.replace(/è|é|ẹ|ẻ|ẽ|ê|ề|ế|ệ|ể|ễ/g, "e");
        str = str.replace(/ì|í|ị|ỉ|ĩ/g, "i");
        str = str.replace(/ò|ó|ọ|ỏ|õ|ô|ồ|ố|ộ|ổ|ỗ|ơ|ờ|ớ|ợ|ở|ỡ/g, "o");
        str = str.replace(/ù|ú|ụ|ủ|ũ|ư|ừ|ứ|ự|ử|ữ/g, "u");
        str = str.replace(/ỳ|ý|ỵ|ỷ|ỹ/g, "y");
        str = str.replace(/đ/g, "d");
        str = str.replace(/\u0300|\u0301|\u0303|\u0309|\u0323/g, "");
        str = str.replace(/\u02C6|\u0306|\u031B/g, "");
        return str.trim();
    }

    function cleanAdminPrefix(str) {
        if (!str) return '';
        var s = removeVietnameseTones(str);
        return s.replace(/^(thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran|tp\.|q\.|h\.|tx\.|p\.|x\.|tt\.)\s+/g, '')
                .replace(/\s+/g, ' ')
                .trim();
    }

    function findBestOption(selectEl, candidates) {
        if (!selectEl || !candidates) return null;
        if (!Array.isArray(candidates)) candidates = [candidates];
        var options = Array.from(selectEl.options).filter(function (o) { return o.value !== ''; });

        // Ưu tiên 1: Trùng khớp chính xác tuyệt đối sau khi lược bỏ tiền tố hành chính
        for (var c = 0; c < candidates.length; c++) {
            var target = cleanAdminPrefix(candidates[c]);
            if (!target) continue;
            for (var i = 0; i < options.length; i++) {
                var optClean = cleanAdminPrefix(options[i].textContent);
                if (optClean === target) return options[i].value;
            }
        }

        // Ưu tiên 2: Trùng khớp một phần chuỗi (chứa nhau)
        for (var c = 0; c < candidates.length; c++) {
            var target = cleanAdminPrefix(candidates[c]);
            if (!target || target.length < 3) continue;
            for (var i = 0; i < options.length; i++) {
                var optClean = cleanAdminPrefix(options[i].textContent);
                if (optClean && (optClean.includes(target) || target.includes(optClean))) return options[i].value;
            }
        }
        return null;
    }

    function showAddressAlert(msg, isSuccess) {
        var alertEl = $('autoAddressAlert');
        var msgEl = $('autoAddressAlertMsg');
        if (!alertEl || !msgEl) return;
        alertEl.className = 'alert ' + (isSuccess ? 'alert-success' : 'alert-warning') + ' mt-2 p-2 small';
        msgEl.innerHTML = (isSuccess ? '<i class="bi bi-check-circle-fill"></i> ' : '<i class="bi bi-info-circle-fill"></i> ') + msg;
        alertEl.classList.remove('d-none');
    }

    function hideAddressAlert() {
        if ($('autoAddressAlert')) $('autoAddressAlert').classList.add('d-none');
    }

    /** Đã chọn đủ khu vực để tính phí giao hàng chưa. */
    function areaSelected() {
        return ghn ? !!(ward && ward.value) : !!($('province') && $('province').value);
    }

    function applyAddressComponents(provCandidates, distCandidates, wardCandidates, roadAddress) {
        if (roadAddress && $('address')) {
            $('address').value = roadAddress;
        }

        if (ghn) {
            var matchedProv = findBestOption(prov, provCandidates);
            if (matchedProv) {
                prov.value = matchedProv;
                var provText = prov.options[prov.selectedIndex] ? prov.options[prov.selectedIndex].textContent : '';
                onProvince(false).then(function () {
                    var matchedDist = findBestOption(dist, distCandidates);
                    if (matchedDist) {
                        dist.value = matchedDist;
                        var distText = dist.options[dist.selectedIndex] ? dist.options[dist.selectedIndex].textContent : '';
                        return onDistrict(false).then(function () {
                            var matchedWard = findBestOption(ward, wardCandidates);
                            var wardText = '';
                            if (matchedWard) {
                                ward.value = matchedWard;
                                wardText = ward.options[ward.selectedIndex] ? ward.options[ward.selectedIndex].textContent : '';
                            }
                            recalc();
                            var parts = [provText, distText, wardText].filter(Boolean);
                            if (parts.length > 0) {
                                showAddressAlert('Đã tự động chọn: <strong>' + parts.join(' &rarr; ') + '</strong>', true);
                            }
                        });
                    } else {
                        recalc();
                        if (provText) {
                            showAddressAlert('Đã tự động chọn Tỉnh/Thành: <strong>' + provText + '</strong>. Vui lòng chọn thêm Quận/Huyện.', false);
                        }
                    }
                });
            }
        } else {
            var pSel = $('province');
            var matched = findBestOption(pSel, provCandidates);
            if (matched) {
                pSel.value = matched;
                recalc();
                var pText = pSel.options[pSel.selectedIndex] ? pSel.options[pSel.selectedIndex].textContent : '';
                showAddressAlert('Đã tự động chọn: <strong>' + pText + '</strong>', true);
            }
        }
    }

    // Khởi tạo Leaflet Icon đường dẫn chuẩn
    if (window.L) {
        delete L.Icon.Default.prototype._getIconUrl;
        L.Icon.Default.mergeOptions({
            iconRetinaUrl: base + '/vendor/leaflet/images/marker-icon-2x.png',
            iconUrl: base + '/vendor/leaflet/images/marker-icon.png',
            shadowUrl: base + '/vendor/leaflet/images/marker-shadow.png',
        });
    }

    var map = null;
    var marker = null;
    var defaultLat = 21.0178; // Trung tâm Đường Láng, Hà Nội
    var defaultLng = 105.8031;

    function initMap() {
        if (map) return;
        var mapEl = $('checkoutMap');
        if (!mapEl) return;

        map = L.map('checkoutMap').setView([defaultLat, defaultLng], 15);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap contributors',
            maxZoom: 19
        }).addTo(map);

        marker = L.marker([defaultLat, defaultLng], { draggable: true }).addTo(map);

        marker.on('dragend', function (e) {
            var pos = marker.getLatLng();
            previewMapLocation(pos.lat, pos.lng);
        });

        map.on('click', function (e) {
            marker.setLatLng(e.latlng);
            previewMapLocation(e.latlng.lat, e.latlng.lng);
        });
    }

    function openMap() {
        var wrapper = $('mapWrapper');
        if (!wrapper) return;
        wrapper.classList.remove('d-none');
        $('btnToggleMapText').textContent = 'Đóng bản đồ';
        if (!map) {
            setTimeout(function () {
                initMap();
                map.invalidateSize();
            }, 100);
        } else {
            setTimeout(function () { map.invalidateSize(); }, 50);
        }
    }

    function toggleMap() {
        var wrapper = $('mapWrapper');
        if (!wrapper) return;
        if (wrapper.classList.contains('d-none')) {
            openMap();
        } else {
            wrapper.classList.add('d-none');
            $('btnToggleMapText').textContent = 'Bản đồ chọn vị trí';
        }
    }

    var pendingMapData = null;

    function applyResolvedData(d, updateAddressField) {
        if (!d) return;

        // 1. Tự động chọn Tỉnh / thành phố
        if (ghn && prov && d.province) {
            prov.value = d.province.id;
            $('province').value = d.province.name;

            // Huỷ các lần tải danh sách đang chờ để không ghi đè kết quả nhận diện
            dist.dataset.seq = String((+dist.dataset.seq || 0) + 1);
            ward.dataset.seq = String((+ward.dataset.seq || 0) + 1);

            // 2. Nạp danh sách và tự động chọn Quận / huyện
            fill(dist, d.districts || [], 'id', d.district ? d.district.id : null, '-- Chọn quận/huyện --');

            // 3. Nạp danh sách và tự động chọn Phường / xã
            fill(ward, d.wards || [], 'code', d.ward ? d.ward.code : null, '-- Chọn phường/xã --');
        } else if (!ghn && $('province') && d.province) {
            $('province').value = d.province.name;
        }

        // 4. Cập nhật phí giao hàng và tổng tiền
        if (d.fee_text && $('shipFee')) $('shipFee').textContent = d.fee_text;
        if (d.total_text && $('grandTotal')) $('grandTotal').textContent = d.total_text;

        // 5. Cập nhật ô địa chỉ giao hàng nếu được yêu cầu (từ nút Áp dụng địa chỉ)
        if (updateAddressField && $('address')) {
            var fullRoad = d.road || [d.ward ? d.ward.name : '', d.district ? d.district.name : '', d.province ? d.province.name : ''].filter(Boolean).join(', ');
            if (fullRoad) $('address').value = fullRoad;
        }

        // 6. Cập nhật bản đồ
        if (d.lat && d.lon) {
            if (map && marker) {
                map.setView([d.lat, d.lon], 16);
                marker.setLatLng([d.lat, d.lon]);
            }
            if ($('mapCoords')) $('mapCoords').textContent = parseFloat(d.lat).toFixed(5) + ', ' + parseFloat(d.lon).toFixed(5);
        }

        // 7. Thông báo trực quan
        var parts = [
            d.province ? d.province.name : '',
            d.district ? d.district.name : '',
            d.ward ? d.ward.name : ''
        ].filter(Boolean);
        var feeStr = d.fee_text ? ' &bull; Phí giao hàng GHN: <strong class="text-danger">' + d.fee_text + '</strong>' : '';
        showAddressAlert('Đã tự động chọn: <strong>' + parts.join(' &rarr; ') + '</strong>' + feeStr, true);
    }

    function previewMapLocation(lat, lon) {
        if ($('mapLoading')) $('mapLoading').classList.remove('d-none');
        var q = new URLSearchParams({lat: lat, lon: lon, shipping_method: pickup() ? 'PICKUP' : 'DELIVERY'});

        return fetch(base + '/shipping/resolve?' + q.toString(), {
            headers: {'Accept': 'application/json'}
        })
        .then(function (r) { return r.json(); })
        .then(function (d) {
            if (!d.ok) {
                if ($('mapSelectedAddressName')) $('mapSelectedAddressName').textContent = 'Không tìm thấy địa chỉ tại điểm này';
                if ($('btnApplyMapAddress')) $('btnApplyMapAddress').disabled = true;
                return;
            }
            pendingMapData = d;
            var parts = [
                d.road || '',
                d.ward ? d.ward.name : '',
                d.district ? d.district.name : '',
                d.province ? d.province.name : ''
            ].filter(Boolean);
            var fullText = parts.join(', ');
            if ($('mapSelectedAddressName')) $('mapSelectedAddressName').textContent = fullText || 'Đã định vị tọa độ';
            if ($('mapCoords')) $('mapCoords').textContent = parseFloat(d.lat).toFixed(5) + ', ' + parseFloat(d.lon).toFixed(5);
            if ($('btnApplyMapAddress')) $('btnApplyMapAddress').disabled = false;
        })
        .catch(function (e) {
            console.warn('Map preview error', e);
        })
        .finally(function () {
            if ($('mapLoading')) $('mapLoading').classList.add('d-none');
        });
    }

    function geolocateUser() {
        if (!navigator.geolocation) {
            alert('Trình duyệt của bạn không hỗ trợ định vị GPS.');
            return;
        }
        if ($('mapLoading')) $('mapLoading').classList.remove('d-none');
        navigator.geolocation.getCurrentPosition(function (pos) {
            var lat = pos.coords.latitude;
            var lon = pos.coords.longitude;
            openMap();
            if (map && marker) {
                map.setView([lat, lon], 16);
                marker.setLatLng([lat, lon]);
            }
            previewMapLocation(lat, lon);
        }, function (err) {
            if ($('mapLoading')) $('mapLoading').classList.add('d-none');
            alert('Không thể lấy vị trí GPS (' + err.message + '). Vui lòng cho phép quyền truy cập vị trí trên trình duyệt hoặc chọn trên bản đồ.');
        }, { timeout: 10000 });
    }

    // Gắn sự kiện các nút
    if ($('btnToggleMap')) $('btnToggleMap').addEventListener('click', toggleMap);
    if ($('btnCloseMap')) $('btnCloseMap').addEventListener('click', toggleMap);
    if ($('btnGeolocate')) $('btnGeolocate').addEventListener('click', geolocateUser);

    // Khi người dùng bấm "Áp dụng địa chỉ này" từ cửa sổ bản đồ
    if ($('btnApplyMapAddress')) {
        $('btnApplyMapAddress').addEventListener('click', function () {
            if (!pendingMapData) return;
            applyResolvedData(pendingMapData, true);
            toggleMap();
        });
    }

    // Khi người dùng gõ địa chỉ giao hàng và ấn Enter hoặc bấm nút Phân tích vị trí
    function doParseFromAddressInput() {
        var val = $('address') ? $('address').value.trim() : '';
        if (!val) {
            alert('Vui lòng nhập địa chỉ vào ô "Địa chỉ giao hàng" trước khi ấn Enter (VD: 1126 Đường Láng).');
            if ($('address')) $('address').focus();
            return;
        }
        if ($('mapLoading')) $('mapLoading').classList.remove('d-none');
        var q = new URLSearchParams({query: val, shipping_method: pickup() ? 'PICKUP' : 'DELIVERY'});
        fetch(base + '/shipping/resolve?' + q.toString(), {
            headers: {'Accept': 'application/json'}
        })
        .then(function (r) { return r.json(); })
        .then(function (d) {
            if (!d.ok) {
                showAddressAlert(areaSelected()
                    ? 'Không nhận diện được khu vực từ ô địa chỉ. Phí giao hàng đang tính theo <strong>Tỉnh / Quận / Phường bạn đã chọn ở trên</strong>, hãy kiểm tra lại cho khớp với địa chỉ nhận hàng.'
                    : (d.message || 'Không tìm thấy địa điểm phù hợp.'), false);
                return;
            }
            pendingMapData = d;
            applyResolvedData(d, false);
            if ($('mapSelectedAddressName')) {
                var parts = [d.road || val, d.ward ? d.ward.name : '', d.district ? d.district.name : '', d.province ? d.province.name : ''].filter(Boolean);
                $('mapSelectedAddressName').textContent = parts.join(', ');
            }
            if ($('btnApplyMapAddress')) $('btnApplyMapAddress').disabled = false;
        })
        .catch(function (e) {
            console.warn('Resolve error', e);
            showAddressAlert('Lỗi kết nối khi nhận diện địa chỉ. Vui lòng thử lại.', false);
        })
        .finally(function () {
            if ($('mapLoading')) $('mapLoading').classList.add('d-none');
        });
    }

    if ($('btnParseAddress')) $('btnParseAddress').addEventListener('click', doParseFromAddressInput);
    if ($('btnAddressToMap')) $('btnAddressToMap').addEventListener('click', doParseFromAddressInput);
    if ($('btnQuickSearchMap')) {
        $('btnQuickSearchMap').addEventListener('click', function () {
            openMap();
            doParseFromAddressInput();
        });
    }

    // Điền địa chỉ giao hàng rồi ấn Enter thì tự set các giá trị quận huyện xã thành phố về chỗ tương ứng
    if ($('address')) {
        $('address').addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault(); // Ngăn submit form đặt hàng
                doParseFromAddressInput();
            }
        });
    }
})();
