package cn.xmcraft.dreamport.server.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** 站内通知（dp_notification），type: invite_received/invite_confirmed/invite_rejected/… */
@Table("dp_notification")
public record NotificationRecord(
        @Id Long id,
        String username,
        String type,
        String title,
        String message,
        Boolean isRead,
        Long createdAt,
        String relatedUsername
) {
    public NotificationRecord {
        if (isRead == null) {
            isRead = false;
        }
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
    }

    public NotificationRecord markRead() {
        return new NotificationRecord(id, username, type, title, message, true, createdAt, relatedUsername);
    }
}
