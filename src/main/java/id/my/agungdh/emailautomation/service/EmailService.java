package id.my.agungdh.emailautomation.service;

import id.my.agungdh.emailautomation.dto.EmailDto;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class EmailService {

    private final Gmail gmail;
    private static final String USER = "me";

    public EmailService(Gmail gmail) {
        this.gmail = gmail;
    }

    /** Ambil email persis 24 jam terakhir, mengikuti timezone sistem (default JVM). */
    public List<EmailDto> getLast24hEmails() throws Exception {
        // cutoff 24 jam (presisi millisecond, timezone sistem)
        Instant now = Instant.now();
        long cutoffMs = now.minusSeconds(24 * 60 * 60).toEpochMilli();

        // Query kasar untuk menyempitkan hasil: pakai tanggal kemarin (berdasarkan timezone sistem)
        // NOTE: after: di Gmail pakai granularity hari, jadi kita tetap filter presisi pakai internalDate di bawah.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String afterStr = yesterday.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String q = "after:" + afterStr;

        Map<String, String> labelMap = fetchLabelMap();

        // paging list message IDs
        List<String> ids = new ArrayList<>();
        Gmail.Users.Messages.List req = gmail.users().messages().list(USER).setQ(q);
        ListMessagesResponse res;
        do {
            res = req.execute();
            if (res.getMessages() != null) {
                for (Message m : res.getMessages()) ids.add(m.getId());
            }
            req.setPageToken(res.getNextPageToken());
        } while (res.getNextPageToken() != null && !res.getNextPageToken().isEmpty());

        List<EmailDto> out = new ArrayList<>();
        for (String id : ids) {
            // Ambil metadata Subject + internalDate
            Message msg = gmail.users().messages()
                    .get(USER, id)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Subject"))
                    .execute();

            Long internalDate = msg.getInternalDate(); // epoch millis (UTC)
            if (internalDate == null || internalDate < cutoffMs) {
                continue; // buang yang lebih tua dari 24 jam
            }

            String subject = "(no subject)";
            if (msg.getPayload() != null && msg.getPayload().getHeaders() != null) {
                subject = msg.getPayload().getHeaders().stream()
                        .filter(h -> "Subject".equalsIgnoreCase(h.getName()))
                        .map(MessagePartHeader::getValue)
                        .findFirst().orElse(subject);
            }

            List<String> labels = new ArrayList<>();
            if (msg.getLabelIds() != null) {
                for (String lid : msg.getLabelIds()) {
                    labels.add(labelMap.getOrDefault(lid, lid));
                }
            }

            out.add(new EmailDto(id, subject, labels));
        }

        // (opsional) urutkan terbaru dulu
        out.sort(Comparator.comparing(EmailDto::id).reversed());
        return out;
    }

    private Map<String, String> fetchLabelMap() throws Exception {
        Map<String, String> map = new HashMap<>();
        ListLabelsResponse lr = gmail.users().labels().list(USER).execute();
        if (lr.getLabels() != null) {
            for (Label l : lr.getLabels()) map.put(l.getId(), l.getName());
        }
        return map;
    }
}
