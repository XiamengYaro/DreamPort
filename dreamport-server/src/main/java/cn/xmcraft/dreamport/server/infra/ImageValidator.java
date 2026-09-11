package cn.xmcraft.dreamport.server.infra;

import java.util.List;

/**
 * 图片上传校验(扩展名白名单 + magic bytes 内容校验,防伪装扩展名)。
 * 从 CommunityController 抽出共用:机器截图/论坛图片/通用上传。
 */
public final class ImageValidator {

    private static final List<String> ALLOWED_EXTS = List.of(".jpg", ".png", ".gif", ".webp");

    private ImageValidator() {
    }

    /** @return 小写扩展名(含点);不合法返回 null */
    public static String detectExt(String filename) {
        if (filename == null) {
            return null;
        }
        String lower = filename.toLowerCase();
        return ALLOWED_EXTS.stream().filter(lower::endsWith).findFirst().orElse(null);
    }

    /** 校验文件头与扩展名一致(防伪装) */
    public static boolean isImage(byte[] b, String ext) {
        if (b == null || b.length < 12) {
            return false;
        }
        return switch (ext) {
            case ".png" -> (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
            case ".jpg" -> (b[0] & 0xFF) == 0xFF && b[1] == 0xD8 && b[2] == 0xFF;
            case ".gif" -> b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
            case ".webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}
