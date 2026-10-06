package com.hieuthuoc.web.admin;

import com.hieuthuoc.service.ReportExportService;
import com.hieuthuoc.service.ReportService;
import com.hieuthuoc.service.SettingService;
import com.hieuthuoc.web.Web;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Báo cáo thống kê: bảng số liệu (index), dữ liệu biểu đồ (charts), bản in, xuất Excel. */
@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportController {
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MY = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ReportService reports;
    private final ReportExportService export;
    private final SettingService settings;

    private void data(String from0, String to0, Model model) {
        LocalDate[] range = ReportService.range(from0, to0);
        int near = settings.getInt("near_expiry_days");
        model.addAttribute("report", reports.build(range[0], range[1], near));
        model.addAttribute("inventory", reports.inventoryValue(near));
        model.addAttribute("nearDays", near);
        model.addAttribute("from", range[0]);
        model.addAttribute("to", range[1]);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String index(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        data(from, to, model);
        LocalDate today = LocalDate.now();
        model.addAttribute("title", "Báo cáo thống kê");
        model.addAttribute("from7", today.minusDays(6));
        model.addAttribute("fromMonth", today.withDayOfMonth(1));
        model.addAttribute("from3m", today.minusMonths(3));
        model.addAttribute("fromYear", today.withDayOfYear(1));
        return "admin/reports";
    }

    private static Map<String, Object> series(Collection<String> labels, Collection<?> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", labels);
        m.put("values", values);
        return m;
    }

    /** Dữ liệu biểu đồ (JSON) cho trang báo cáo. */
    @GetMapping("/charts")
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> charts(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        LocalDate[] range = ReportService.range(from, to);
        ReportService.Report r = reports.build(range[0], range[1], settings.getInt("near_expiry_days"));
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> day = new LinkedHashMap<>();
        day.put("labels", r.getByDay().keySet().stream().map(d -> LocalDate.parse(d).format(DM)).toList());
        day.put("revenue", r.getByDay().values().stream().map(v -> v[1]).toList());
        day.put("orders", r.getByDay().values().stream().map(v -> v[0]).toList());
        out.put("byDay", day);
        out.put("byMonth", series(r.getByMonth().keySet().stream().map(m -> LocalDate.parse(m + "-01").format(MY)).toList(), r.getByMonth().values()));
        out.put("byYear", series(r.getByYear().keySet(), r.getByYear().values()));
        out.put("byCategory", series(r.getByCategory().keySet(), r.getByCategory().values()));
        out.put("byPayment", series(r.getByPayment().keySet(), r.getByPayment().values().stream().map(v -> v[1]).toList()));
        out.put("byChannel", series(r.getByChannel().keySet(), r.getByChannel().values().stream().map(v -> v[1]).toList()));
        out.put("topProducts", series(r.getTopProducts().stream().map(ReportService.ProductRow::name).toList(),
                r.getTopProducts().stream().map(ReportService.ProductRow::quantity).toList()));
        return out;
    }

    @GetMapping("/print")
    @Transactional(readOnly = true)
    public String print(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        data(from, to, model);
        model.addAttribute("title", "Báo cáo");
        model.addAttribute("printedAt", LocalDateTime.now());
        return "admin/report-print";
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportXlsx(@RequestParam(required = false) String from, @RequestParam(required = false) String to) throws IOException {
        LocalDate[] range = ReportService.range(from, to);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        export.report(range[0], range[1], out);
        return Web.xlsx(out, "bao-cao-" + range[0].format(YMD) + "-" + range[1].format(YMD) + ".xlsx");
    }

    @GetMapping("/national")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportNational(@RequestParam(required = false) String from, @RequestParam(required = false) String to) throws IOException {
        LocalDate[] range = ReportService.range(from, to);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        export.national(range[0], range[1], out);
        return Web.xlsx(out, "lien-thong-duoc-" + range[0].format(YMD) + "-" + range[1].format(YMD) + ".xlsx");
    }
}
