package id.my.agungdh.emailautomation.controller;

import id.my.agungdh.emailautomation.dto.EmailDto;
import id.my.agungdh.emailautomation.service.EmailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EmailController {
    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    /** contoh: GET /api/emails/last-24h/by-label?label=%2BProjectX */
    @GetMapping("/api/emails/last-24h/by-label")
    public List<EmailDto> last24hByLabel(@RequestParam("label") String label) throws Exception {
        return emailService.getLast24hEmailsByLabel(label);
    }
}
