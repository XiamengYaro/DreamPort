package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.infra.ImageValidator;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * 通用图片上传(登录用户,公开论坛帖子/回复等 UGC 场景):
 * ≤5MB、扩展名白名单、magic bytes 校验、UUID 文件名落 static/uploads。
 * 管理端/机器截图各有自己的上传通道,此处为 UGC 通用入口。
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @PostMapping("/image")
    public ResponseEntity<Object> image(@RequestParam("file") MultipartFile file,
                                        HttpServletRequest request) {
        String me = AuthUtil.currentUser(request);
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.failure("未登录"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("文件为空"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("图片不能超过 5MB"));
        }
        String ext = ImageValidator.detectExt(file.getOriginalFilename());
        if (ext == null) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("仅支持 jpg/png/gif/webp"));
        }
        try {
            if (!ImageValidator.isImage(file.getBytes(), ext)) {
                return ResponseEntity.badRequest().body(ApiResponse.failure("文件内容与扩展名不符"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.failure("文件读取失败"));
        }
        String filename = UUID.randomUUID() + ext;
        try {
            Path dir = Path.of("static", "uploads");
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.failure("上传失败"));
        }
        return ResponseEntity.ok(ApiResponse.success("上传成功", Map.of("url", "/uploads/" + filename)));
    }
}
