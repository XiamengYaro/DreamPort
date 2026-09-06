package cn.xmcraft.dreamport.common;

import java.util.List;

/**
 * 游戏收件箱轮询响应（GET /internal/v1/messages/pending?since=）：
 * seq 单调递增，插件按游标增量拉取后 broadcastMessage。
 * docs/ASTRBOT_PLAN.md §5.4
 */
public record PendingMessagesResponse(List<Message> messages, long latest) {

    public record Message(long seq, String text) {
    }
}
