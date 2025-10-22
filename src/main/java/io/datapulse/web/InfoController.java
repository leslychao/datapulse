package io.datapulse.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class InfoController {

    @GetMapping("/api/info")
    public ResponseEntity<Map<String, Object>> info(@RequestParam(value = "name", required = false) String name) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("service", "datapulse");
        resp.put("time", Instant.now().toString());
        resp.put("greeting", StringUtils.isBlank(name) ? "hello" : "hello, " + name);
        return ResponseEntity.ok(resp);
    }
}