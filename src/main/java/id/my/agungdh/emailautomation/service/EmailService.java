package id.my.agungdh.emailautomation.service;

import id.my.agungdh.emailautomation.dto.EmailDto;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class EmailService {
    private final Gmail gmail;
    private static final String USER = "me";

    public EmailService(Gmail gmail) {
        this.gmail = gmail;
    }

    /** 24 jam terakhir dengan filter label tertentu (nama label apa adanya, termasuk yang diawali '+'). */
    public List<EmailDto> getLast24hEmailsByLabel(String labelName) throws Exception {
        // cutoff presisi 24 jam
        long cutoffMs = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();

        // ===== Query Gmail =====
        // 1) label:"<nama>"  --> karena label bisa mengandung '+', spasi, dsb (WAJIB pakai kutip)
        // 2) newer_than:1d   --> perkecil hasil
        // 3) (opsional tambahan) after:YYYY/MM/DD untuk mengurangi page kalau mailbox sangat besar
        String quotedLabel = "\"" + labelName + "\"";
        String q = "label:" + quotedLabel + " newer_than:1d";

        // (opsional) tambah after: kemarin untuk penyempitan ekstra
        String afterStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        q += " after:" + afterStr;

        Map<String, String> labelMap = fetchLabelMap();

        // paging list message IDs
        List<String> ids = new ArrayList<>();
        Gmail.Users.Messages.List req = gmail.users().messages().list(USER).setQ(q).setMaxResults(100L);
        ListMessagesResponse res;
        do {
            res = req.execute();
            if (res.getMessages() != null) {
                for (Message m : res.getMessages()) ids.add(m.getId());
            }
            req.setPageToken(res.getNextPageToken());
        } while (res.getNextPageToken() != null && !res.getNextPageToken().isEmpty());

        // ambil metadata Subject + internalDate + labelIds → DTO
        List<EmailDto> out = new ArrayList<>();
        for (String id : ids) {
            Message msg = gmail.users().messages()
                    .get(USER, id)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Subject"))
                    .execute();

            long internalDate = msg.getInternalDate() != null ? msg.getInternalDate() : 0L;
            if (internalDate < cutoffMs) continue; // pastikan benar2 ≤ 24 jam

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

            out.add(new EmailDto(id, subject, labels, internalDate));
        }

        // urutkan terbaru dulu (berdasarkan internalDate)
        out.sort(Comparator.comparingLong(EmailDto::internalDateMs).reversed());
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
