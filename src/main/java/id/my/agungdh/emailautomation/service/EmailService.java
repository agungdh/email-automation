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

    /** SEMUA email 24 jam terakhir, lengkap labels + plusTags. */
    public List<EmailDto> getLast24hAllEmails() throws Exception {
        long cutoffMs = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();

        String q = "newer_than:1d";
        String afterStr = LocalDate.now().minusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        q += " after:" + afterStr;

        Map<String, String> labelMap = fetchLabelMap();
        List<String> ids = listMessageIds(q);

        Pattern plusTagPattern = Pattern.compile("^[^+@]+\\+([^@]+)@.+$");

        List<EmailDto> out = new ArrayList<>();
        for (String id : ids) {
            Message msg = gmail.users().messages()
                    .get(USER, id)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Subject", "Delivered-To", "To", "Cc"))
                    .execute();

            long internalDate = msg.getInternalDate() != null ? msg.getInternalDate() : 0L;
            if (internalDate < cutoffMs) continue;

            String subject = extractHeader(msg, "Subject").orElse("(no subject)");
            List<String> labels = mapLabelNames(labelMap, msg.getLabelIds());
            List<String> deliveredTo = extractAllHeaders(msg, "Delivered-To");
            List<String> plusTags = extractPlusTags(msg, plusTagPattern);

            out.add(new EmailDto(id, subject, labels, internalDate, deliveredTo, plusTags));
        }

        out.sort(Comparator.comparingLong(EmailDto::internalDateMs).reversed());
        return out;
    }

    /** HANYA email 24 jam terakhir yang punya plusTag. */
    public List<EmailDto> getLast24hEmailsWithPlusOnly() throws Exception {
        long cutoffMs = Instant.now().minusSeconds(24 * 60 * 60).toEpochMilli();

        String q = "newer_than:1d";
        String afterStr = LocalDate.now().minusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        q += " after:" + afterStr;

        Map<String, String> labelMap = fetchLabelMap();
        List<String> ids = listMessageIds(q);

        Pattern plusTagPattern = Pattern.compile("^[^+@]+\\+([^@]+)@.+$");

        List<EmailDto> out = new ArrayList<>();
        for (String id : ids) {
            Message msg = gmail.users().messages()
                    .get(USER, id)
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Subject", "Delivered-To", "To", "Cc"))
                    .execute();

            long internalDate = msg.getInternalDate() != null ? msg.getInternalDate() : 0L;
            if (internalDate < cutoffMs) continue;

            List<String> plusTags = extractPlusTags(msg, plusTagPattern);
            if (plusTags.isEmpty()) continue;

            String subject = extractHeader(msg, "Subject").orElse("(no subject)");
            List<String> labels = mapLabelNames(labelMap, msg.getLabelIds());
            List<String> deliveredTo = extractAllHeaders(msg, "Delivered-To");

            out.add(new EmailDto(id, subject, labels, internalDate, deliveredTo, plusTags));
        }

        out.sort(Comparator.comparingLong(EmailDto::internalDateMs).reversed());
        return out;
    }

    // ===== Helpers =====

    private List<String> listMessageIds(String q) throws Exception {
        List<String> ids = new ArrayList<>();
        Gmail.Users.Messages.List req = gmail.users().messages()
                .list(USER)
                .setQ(q)
                .setMaxResults(100L);
        ListMessagesResponse res;
        do {
            res = req.execute();
            if (res.getMessages() != null) res.getMessages().forEach(m -> ids.add(m.getId()));
            req.setPageToken(res.getNextPageToken());
        } while (res.getNextPageToken() != null && !res.getNextPageToken().isEmpty());
        return ids;
    }

    private Map<String, String> fetchLabelMap() throws Exception {
        Map<String, String> map = new HashMap<>();
        ListLabelsResponse lr = gmail.users().labels().list(USER).execute();
        if (lr.getLabels() != null) for (Label l : lr.getLabels()) map.put(l.getId(), l.getName());
        return map;
    }

    private List<String> mapLabelNames(Map<String, String> map, List<String> ids) {
        List<String> names = new ArrayList<>();
        if (ids != null) for (String id : ids) names.add(map.getOrDefault(id, id));
        return names;
    }

    private Optional<String> extractHeader(Message msg, String name) {
        if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) return Optional.empty();
        return msg.getPayload().getHeaders().stream()
                .filter(h -> name.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst();
    }

    private List<String> extractAllHeaders(Message msg, String name) {
        List<String> vals = new ArrayList<>();
        if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) return vals;
        for (MessagePartHeader h : msg.getPayload().getHeaders()) {
            if (name.equalsIgnoreCase(h.getName()) && h.getValue() != null && !h.getValue().isBlank()) {
                vals.add(h.getValue());
            }
        }
        return vals;
    }

    private List<String> extractPlusTags(Message msg, Pattern plusTagPattern) {
        List<String> plusTags = new ArrayList<>();
        if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) return plusTags;

        for (MessagePartHeader h : msg.getPayload().getHeaders()) {
            String n = h.getName();
            String v = h.getValue();
            if (v == null || v.isBlank()) continue;

            if ("Delivered-To".equalsIgnoreCase(n)) {
                extractPlusTagFromAddress(v, plusTagPattern).ifPresent(tag -> addIfAbsent(plusTags, tag));
            }
            if ("To".equalsIgnoreCase(n) || "Cc".equalsIgnoreCase(n)) {
                for (String addr : v.split(",")) {
                    extractPlusTagFromAddress(addr.trim(), plusTagPattern).ifPresent(tag -> addIfAbsent(plusTags, tag));
                }
            }
        }
        return plusTags;
    }

    private Optional<String> extractPlusTagFromAddress(String address, Pattern pattern) {
        // tangani format "Nama <email@domain>"
        String a = address;
        int lt = a.indexOf('<'), gt = a.indexOf('>');
        if (lt >= 0 && gt > lt) a = a.substring(lt + 1, gt);
        a = a.trim();
        Matcher m = pattern.matcher(a);
        if (m.matches()) return Optional.of(m.group(1));
        return Optional.empty();
    }

    private static void addIfAbsent(List<String> list, String val) {
        if (!list.contains(val)) list.add(val);
    }
}
