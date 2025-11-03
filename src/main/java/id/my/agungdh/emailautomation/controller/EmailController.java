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

    /** Ambil SEMUA email 24 jam terakhir + kembalikan labels & plus-tags (jika ada). */
    @GetMapping("/api/emails/last-24h")
    public List<EmailDto> last24hAll() throws Exception {
        return emailService.getLast24hAllEmails();
    }
}
