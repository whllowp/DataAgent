package com.dataagent.web;

import com.dataagent.service.PiiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pii")
public class PiiController {

    private final PiiService pii;

    public PiiController(PiiService pii) {
        this.pii = pii;
    }

    @GetMapping("/classify")
    public Map<String, Object> classify(@RequestParam String column,
                                        @RequestParam(required = false) String label) {
        String type = pii.classify(column, label);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("column", column);
        body.put("type", type == null ? "NONE" : type);
        body.put("masked", type != null);
        body.put("maskExample", type == null ? null : pii.mask(type, "example@example.com"));
        return body;
    }
}
