package com.fingermemory.server.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaRoutingController {
    @GetMapping({"/gm", "/gm/"})
    public String gm() {
        return "forward:/index.html";
    }
}
