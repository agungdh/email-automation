package id.my.agungdh.emailautomation.controller;

import id.my.agungdh.emailautomation.dto.EmailDto;
import id.my.agungdh.emailautomation.service.EmailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/api/emails/last-24h")
    public List<EmailDto> last24h() throws Exception {
        return emailService.getLast24hEmails();
    }
}
