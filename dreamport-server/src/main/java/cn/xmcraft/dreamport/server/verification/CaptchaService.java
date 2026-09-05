package cn.xmcraft.dreamport.server.verification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 算式图形验证码（math 型，5 分钟有效——对齐旧版 CaptchaService）。
 * 输出 Base64 PNG + token，内存态，验证即消费。
 */
@Service
public class CaptchaService {

    private static final long TTL_MS = 5 * 60_000L;

    public record CaptchaImage(String token, String imageBase64, long expiresAt) {
    }

    private record Entry(int answer, long expiresAt) {
    }

    private final Map<String, Entry> captchas = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public CaptchaImage generate() {
        int a = random.nextInt(1, 21);
        int b = random.nextInt(1, 21);
        int op = random.nextInt(3);
        String text;
        int answer;
        switch (op) {
            case 0 -> { text = a + "+" + b + "=?"; answer = a + b; }
            case 1 -> {
                int max = Math.max(a, b);
                int min = Math.min(a, b);
                text = max + "-" + min + "=?";
                answer = max - min;
            }
            default -> {
                int small = random.nextInt(2, 10);
                int big = random.nextInt(2, 10);
                text = small + "×" + big + "=?";
                answer = small * big;
            }
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        captchas.put(token, new Entry(answer, System.currentTimeMillis() + TTL_MS));
        return new CaptchaImage(token, Base64.getEncoder().encodeToString(render(text)), System.currentTimeMillis() + TTL_MS);
    }

    public boolean check(String token, String answer) {
        Entry entry = token == null ? null : captchas.remove(token);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) {
            return false;
        }
        try {
            return entry.answer() == Integer.parseInt(answer.trim());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private byte[] render(String text) {
        int w = 160;
        int h = 48;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(200)));
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));
        }
        g.setColor(new Color(35, 55, 90));
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 26));
        int x = 18 + random.nextInt(10);
        for (char c : text.toCharArray()) {
            g.drawString(String.valueOf(c), x, 32 + random.nextInt(6) - 3);
            x += g.getFontMetrics().charWidth(c) + 4;
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("验证码渲染失败", e);
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        captchas.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }
}
