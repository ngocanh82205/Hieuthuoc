/* Biểu đồ dùng chung (Chart.js): mỗi biểu đồ một chuỗi số liệu, một trục, cột mảnh bo góc 4px, tooltip khi di chuột. */
(function () {
    'use strict';
    var css = getComputedStyle(document.documentElement);
    var v = function (name, fb) { return (css.getPropertyValue(name) || '').trim() || fb; };
    var SERIES = v('--viz-series-1', '#2a78d6');
    var GRID = v('--viz-grid', 'rgba(0,0,0,.06)');
    var TEXT = v('--viz-text-secondary', '#52514e');
    var fmtMoney = function (n) { return Number(n).toLocaleString('vi-VN') + ' ₫'; };
    var fmtShort = function (n) {
        n = Number(n);
        if (Math.abs(n) >= 1e9) return (n / 1e9).toLocaleString('vi-VN', {maximumFractionDigits: 1}) + ' tỷ';
        if (Math.abs(n) >= 1e6) return (n / 1e6).toLocaleString('vi-VN', {maximumFractionDigits: 1}) + ' tr';
        if (Math.abs(n) >= 1e3) return (n / 1e3).toLocaleString('vi-VN', {maximumFractionDigits: 0}) + 'k';
        return n.toLocaleString('vi-VN');
    };

    /**
     * opts: {horizontal, money (bool), label (tên chuỗi, hiện trong tooltip), extra: {label, values} (dòng phụ trong tooltip, VD số đơn)}
     */
    window.vpBar = function (el, labels, values, opts) {
        opts = opts || {};
        if (!window.Chart || !el) return null;
        var horizontal = !!opts.horizontal;
        var valueAxis = {beginAtZero: true, grid: {color: GRID}, border: {display: false},
            ticks: {color: TEXT, callback: function (x) { return opts.money ? fmtShort(x) : Number(x).toLocaleString('vi-VN'); }, precision: 0}};
        var catAxis = {grid: {display: false}, border: {color: GRID}, ticks: {color: TEXT, autoSkip: true, maxRotation: 0}};
        return new Chart(el, {
            type: 'bar',
            data: {labels: labels, datasets: [{label: opts.label || '', data: values, backgroundColor: SERIES, hoverBackgroundColor: SERIES,
                borderRadius: 4, borderSkipped: 'start', maxBarThickness: horizontal ? 18 : 28, categoryPercentage: .8, barPercentage: .9}]},
            options: {
                indexAxis: horizontal ? 'y' : 'x',
                maintainAspectRatio: false,
                interaction: {mode: 'index', intersect: false},
                scales: horizontal ? {x: valueAxis, y: catAxis} : {x: catAxis, y: valueAxis},
                plugins: {
                    legend: {display: false},
                    tooltip: {callbacks: {
                        label: function (c) {
                            var val = horizontal ? c.parsed.x : c.parsed.y;
                            return (opts.label ? opts.label + ': ' : '') + (opts.money ? fmtMoney(val) : Number(val).toLocaleString('vi-VN'));
                        },
                        afterLabel: function (c) {
                            return opts.extra ? opts.extra.label + ': ' + Number(opts.extra.values[c.dataIndex]).toLocaleString('vi-VN') : '';
                        }
                    }}
                }
            }
        });
    };
})();
