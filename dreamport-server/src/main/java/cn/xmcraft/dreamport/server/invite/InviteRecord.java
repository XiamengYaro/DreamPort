package cn.xmcraft.dreamport.server.invite;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.concurrent.ThreadLocalRandom;

/** 邀请码（dp_invite），状态机 active → used（对齐旧版 InviteData） */
@Table("dp_invite")
public record InviteRecord(
        @Id Long id,
        String code,
        String inviterUsername,
        String inviteeUsername,
        String status,
        Long createdAt,
        Long expiresAt,
        Long usedAt
) {
    public InviteRecord {
        if (createdAt == null) {
            createdAt = System.currentTimeMillis();
        }
    }

    public static String generateCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public boolean active(long now) {
        return "active".equals(status) && expiresAt > now;
    }

    public InviteRecord withInvitee(String invitee, String newStatus, long now) {
        return new InviteRecord(id, code, inviterUsername, invitee, newStatus, createdAt, expiresAt, now);
    }
}
