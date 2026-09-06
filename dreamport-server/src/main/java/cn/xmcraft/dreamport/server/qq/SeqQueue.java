package cn.xmcraft.dreamport.server.qq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 有界序号队列（内存）：offer 分配递增 seq；容量满淘汰最旧；按 seq &gt; since 拉取。
 * 用于 QQ 出站与游戏收件箱——聊天属瞬态数据，重启丢失可接受（docs/ASTRBOT_PLAN.md §5.3）。
 */
public final class SeqQueue<T> {

    public record Item<T>(long seq, T payload) {
    }

    private final ArrayDeque<Item<T>> deque = new ArrayDeque<>();
    private final int capacity;
    private long lastSeq = 0;

    public SeqQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity 必须 >= 1");
        }
        this.capacity = capacity;
    }

    /** 入队并分配序号，@return 本条目 */
    public synchronized Item<T> offer(T payload) {
        lastSeq++;
        deque.addLast(new Item<>(lastSeq, payload));
        while (deque.size() > capacity) {
            deque.removeFirst();
        }
        return deque.peekLast();
    }

    /** @return seq &gt; since 的条目（按序） */
    public synchronized List<Item<T>> since(long since) {
        List<Item<T>> result = new ArrayList<>();
        for (Item<T> item : deque) {
            if (item.seq() > since) {
                result.add(item);
            }
        }
        return result;
    }

    /** 最近一次分配的序号（空闲时无增长） */
    public synchronized long latest() {
        return lastSeq;
    }
}
