package id.my.agungdh.emailautomation.dto;

import java.util.List;

public record EmailDto(
        String id,
        String subject,
        List<String> labels,
        long internalDateMs // epoch millis UTC dari Gmail
) {}
