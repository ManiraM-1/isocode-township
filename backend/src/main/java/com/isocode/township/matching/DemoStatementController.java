package com.isocode.township.matching;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoStatementController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final DemoStatementGenerator generator;
    private final Clock clock;

    public DemoStatementController(DemoStatementGenerator generator, Clock clock) {
        this.generator = generator;
        this.clock = clock;
    }

    @GetMapping("/api/desk/demo/bank-statement.csv")
    public ResponseEntity<byte[]> demoStatement() {
        String fileName = "demo-bank-statement-" + LocalDate.now(clock) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(generator.generate());
    }
}
