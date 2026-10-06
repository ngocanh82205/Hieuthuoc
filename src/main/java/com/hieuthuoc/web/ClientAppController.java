package com.hieuthuoc.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Ứng dụng client SPA (static/app) tiêu thụ REST API: /app và /app/ trỏ tới trang index. */
@Controller
public class ClientAppController {
    @GetMapping({"/app", "/app/"})
    public String app() {
        return "forward:/app/index.html";
    }
}
