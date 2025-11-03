package id.my.agungdh.emailautomation.service;

import id.my.agungdh.emailautomation.dto.EmailDto;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.springframework.stereotype.Service;

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

    public List<EmailDto> getLastWeekEmails() throws Exception {
        // rentang 7 hari terakhir (Asia/Jakarta)
        ZoneId tz = ZoneId.of("Asia/Jakarta");
        LocalDate end = LocalDate.now(tz);        // exclusive
        LocalDate start = end.minusDays(7);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");

        String q = "after:" + start.format(fmt) + " before:" + end.format(fmt);

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

        // ambil metadata Subject + labelIds
        List<EmailDto> out = new ArrayList<>();
        for (String id : ids) {
            Message msg = gmail.users().messages()
                    .get(USER, id)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Subject"))
                    .execute();

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
