package id.my.agungdh.emailautomation.dto;

import java.util.List;

public record EmailDto(
        String id,
        String subject,
        List<String> labels,     // label Gmail (INBOX, user labels, dsb.)
        long internalDateMs,     // epoch millis (UTC) dari Gmail
        List<String> deliveredTo, // daftar nilai header Delivered-To (jika ada)
        List<String> plusTags     // tag hasil ekstraksi dari alamat model nama+tag@domain
) {}
