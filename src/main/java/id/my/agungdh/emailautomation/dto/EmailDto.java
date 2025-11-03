package id.my.agungdh.emailautomation.dto;

import java.util.List;

public record EmailDto(
        String id,
        String subject,
        List<String> labels,     // nama label Gmail
        long internalDateMs,     // epoch millis (UTC)
        List<String> deliveredTo,
        List<String> plusTags    // tag dari alamat model nama+tag@domain
) {}
