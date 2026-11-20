package com.fingermemory.server.content;

import com.fingermemory.server.admin.GmAuditEventRow;
import com.fingermemory.server.admin.GmMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GmContentAuditService {
    private final GmMapper gmMapper;
    private final Clock clock;

    public GmContentAuditService(GmMapper gmMapper, Clock clock) {
        this.gmMapper = gmMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejected(
            String actorId,
            String action,
            String target,
            String requestId,
            String reason,
            String diagnostic) {
        gmMapper.insertAudit(new GmAuditEventRow(
                UUID.randomUUID().toString(),
                actorId,
                truncate(action, 64),
                truncate(target, 120),
                "content",
                truncate(
                        requestId == null || requestId.isBlank()
                                ? UUID.randomUUID().toString()
                                : requestId,
                        120),
                rejectionReason(reason, diagnostic),
                "rejected",
                clock.instant().toEpochMilli()));
    }

    private String rejectionReason(String reason, String diagnostic) {
        String normalizedReason = reason == null || reason.isBlank()
                ? "content action rejected"
                : reason.trim();
        String normalizedDiagnostic = diagnostic == null || diagnostic.isBlank()
                ? "rejected"
                : diagnostic.trim();
        return truncate(normalizedReason + " | " + normalizedDiagnostic, 300);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
