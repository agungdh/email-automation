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

    /** Semua email 24 jam terakhir (ikut timezone sistem) + labels & plusTags. */
    @GetMapping("/api/emails/last-24h")
    public List<EmailDto> last24hAll() throws Exception {
        return emailService.getLast24hAllEmails();
    }

    /** Hanya email 24 jam terakhir yang punya plus-tag (nama+tag@domain). */
    @GetMapping("/api/emails/last-24h/with-plus")
    public List<EmailDto> last24hWithPlus() throws Exception {
        return emailService.getLast24hEmailsWithPlusOnly();
    }
}
