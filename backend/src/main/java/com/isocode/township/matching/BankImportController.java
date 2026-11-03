package com.isocode.township.matching;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.matching.dto.BankImportDetails;
import com.isocode.township.matching.dto.BankImportSummary;
import com.isocode.township.matching.dto.BankRowView;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/desk")
public class BankImportController {

    private final BankImportService importService;
    private final BankImportViews views;

    public BankImportController(BankImportService importService, BankImportViews views) {
        this.importService = importService;
        this.views = views;
    }

    @PostMapping(path = "/bank-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BankImportDetails importStatement(@AuthenticationPrincipal SessionUser user,
                                             @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException unreadable) {
            throw new BadRequestException("The uploaded file could not be read.");
        }
        BankImport bankImport = importService.importFile(file.getOriginalFilename(), content, user.userId());
        return views.details(bankImport.getId());
    }

    @GetMapping("/bank-imports")
    public List<BankImportSummary> imports() {
        return views.summaries();
    }

    @GetMapping("/bank-imports/{importId}")
    public BankImportDetails importDetails(@PathVariable Long importId) {
        return views.details(importId);
    }

    @GetMapping("/accounts-queue")
    public List<BankRowView> accountsQueue() {
        return views.queue();
    }
}
