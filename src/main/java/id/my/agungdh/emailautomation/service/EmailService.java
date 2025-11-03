package id.my.agungdh.emailautomation.service;

import id.my.agungdh.emailautomation.dto.EmailDto;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailService {
    private final Gmail gmail;
    private static final String USER = "me";

    public EmailService(Gmail gmail) {
        this.gmail = gmail;
    }

    /** Ambil SEMUA email 24 jam terakhir (ikut timezone sistem), sertakan labels & plus-tags. */
    public List<EmailDto> getLast24hAllEmails() throws Exception {
        long cutoffMs = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();

        // Persempit hasil di sisi server:
        // newer_than:1d sudah cukup; after:kemarin membantu paging untuk mailbox besar.
        String q = "newer_than:1d";
        String afterStr = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        q += " after:" + afterStr;

        Map<String, String> labelMap = fetchLabelMap();

        // paging daftar IDs
        List<String> ids = new ArrayList<>();
        Gmail.Users.Messages.List req = gmail.users().messages()
                .list(USER)
                .setQ(q)
                .setMaxResults(100L);
        ListMessagesResponse res;
        do {
            res = req.execute();
            if (res.getMessages() != null) {
                for (Message m : res.getMessages()) ids.add(m.getId());
            }
            req.setPageToken(res.getNextPageToken());
        } while (res.getNextPageToken() != null && !res.getNextPageToken().isEmpty());

        // Siapkan regex ekstraksi plus-tag dari alamat email (local-part)
        Pattern plusTagPattern = Pattern.compile("^[^+@]+\\+([^@]+)@.+$");

        List<EmailDto> out = new ArrayList<>();
        for (String id : ids) {
            Message msg = gmail.users().messages()
                    .get(USER, id)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Subject", "Delivered-To", "To", "Cc"))
                    .execute();

            long internalDate = msg.getInternalDate() != null ? msg.getInternalDate() : 0L;
            if (internalDate < cutoffMs) continue; // pastikan benar2 24 jam terakhir

            // Subject
            String subject = "(no subject)";
            if (msg.getPayload() != null && msg.getPayload().getHeaders() != null) {
                subject = msg.getPayload().getHeaders().stream()
                        .filter(h -> "Subject".equalsIgnoreCase(h.getName()))
                        .map(MessagePartHeader::getValue)
                        .findFirst().orElse(subject);
            }

            // Labels → nama label
            List<String> labels = new ArrayList<>();
            if (msg.getLabelIds() != null) {
                for (String lid : msg.getLabelIds()) {
                    labels.add(labelMap.getOrDefault(lid, lid));
                }
            }

            // Kumpulkan header Delivered-To (bisa multiple), juga To/Cc untuk jaga-jaga
            List<String> deliveredTo = new ArrayList<>();
            List<String> plusTags = new ArrayList<>();
            if (msg.getPayload() != null && msg.getPayload().getHeaders() != null) {
                for (MessagePartHeader h : msg.getPayload().getHeaders()) {
                    String name = h.getName();
                    String val = h.getValue();
                    if (val == null || val.isBlank()) continue;

                    // Ambil Delivered-To (bisa lebih dari satu)
                    if ("Delivered-To".equalsIgnoreCase(name)) {
                        deliveredTo.add(val);
                        extractPlusTag(val, plusTagPattern).ifPresent(plusTags::add);
                    }

                    // (opsional) coba dari To & Cc juga, karena sebagian email tidak punya Delivered-To
                    if ("To".equalsIgnoreCase(name) || "Cc".equalsIgnoreCase(name)) {
                        // bisa berisi beberapa alamat, pisah dengan koma
                        for (String addr : val.split(",")) {
                            String trimmed = addr.trim();
                            // simpan yang mengandung '+', tapi tidak duplikasi
                            extractPlusTag(trimmed, plusTagPattern).ifPresent(tag -> {
                                if (!plusTags.contains(tag)) plusTags.add(tag);
                            });
                        }
                    }
                }
            }

            out.add(new EmailDto(id, subject, labels, internalDate, deliveredTo, plusTags));
        }

        // Urut terbaru dulu (berdasarkan internalDate)
        out.sort(Comparator.comparingLong(EmailDto::internalDateMs).reversed());
        return out;
    }

    private Optional<String> extractPlusTag(String address, Pattern pattern) {
        // Normalisasi: ambil bagian di dalam <...> kalau formatnya "Nama <email@domain>"
        String addr = address;
        int lt = addr.indexOf('<');
        int gt = addr.indexOf('>');
        if (lt >= 0 && gt > lt) {
            addr = addr.substring(lt + 1, gt);
        }
        addr = addr.trim();
        Matcher m = pattern.matcher(addr);
        if (m.matches()) {
            return Optional.of(m.group(1));
        }
        return Optional.empty();
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
