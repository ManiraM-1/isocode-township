package com.isocode.township.committee;

import com.isocode.township.committee.dto.AgeingFlatsResponse;
import com.isocode.township.committee.dto.CommitteeOverview;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/committee")
public class CommitteeController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final CommitteeService committeeService;

    public CommitteeController(CommitteeService committeeService) {
        this.committeeService = committeeService;
    }

    @GetMapping("/overview")
    public CommitteeOverview overview(@RequestParam(required = false) String month) {
        return committeeService.overview(month);
    }

    @GetMapping("/ageing/{bucket}")
    public AgeingFlatsResponse ageing(@PathVariable String bucket, @RequestParam(required = false) String month) {
        return committeeService.ageingBucket(bucket, month);
    }

    @GetMapping("/report.csv")
    public ResponseEntity<String> report(@RequestParam(required = false) String month) {
        CommitteeOverview overview = committeeService.overview(month);
        String fileName = "committee-report-" + overview.month() + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(CommitteeReportCsv.render(overview));
    }
}
