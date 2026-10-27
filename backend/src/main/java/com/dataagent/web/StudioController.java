package com.dataagent.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StudioController {

    @GetMapping({"/", "/studio", "/studio/"})
    public String studio() {
        return "forward:/studio/index.html";
    }
}
