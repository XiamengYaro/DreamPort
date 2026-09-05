package cn.xmcraft.dreamport.server.api;

import cn.xmcraft.dreamport.server.docs.DocsService;
import cn.xmcraft.dreamport.server.security.AuthUtil;
import cn.xmcraft.dreamport.server.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文档中心（契约对齐旧版 /api/docs 读取 + /api/admin/docs/* 管理）。
 */
@RestController
public class DocsController {

    private final DocsService docsService;

    public DocsController(DocsService docsService) {
        this.docsService = docsService;
    }

    public record DocBody(String title, String category, String filename, String content) {
    }

    public record CategoryBody(String name, String dirName) {
    }

    public record ReorderBody(String type, java.util.List<String> items) {
    }

    /** 公开读取：列表 */
    @GetMapping("/api/docs")
    public ResponseEntity<Object> list() {
        return ResponseEntity.ok(docsService.listAll());
    }

    /** 公开读取：单篇（?category=&filename=） */
    @GetMapping("/api/docs/detail")
    public ResponseEntity<Object> detail(@RequestParam(required = false) String category,
                                         @RequestParam String filename) {
        try {
            return ResponseEntity.ok(docsService.read(category, filename));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("文档不存在"));
        }
    }

    @PostMapping("/api/admin/docs/create")
    public ResponseEntity<Object> create(@RequestBody DocBody body, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("已创建",
                    docsService.create(body.title(), body.category(), body.content())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/api/admin/docs/update")
    public ResponseEntity<Object> update(@RequestBody DocBody body, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        try {
            docsService.update(body.category(), body.filename(), body.content());
            return ResponseEntity.ok(ApiResponse.success("已保存"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/api/admin/docs/delete")
    public ResponseEntity<Object> delete(@RequestBody DocBody body, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        try {
            docsService.delete(body.category(), body.filename());
            return ResponseEntity.ok(ApiResponse.success("已删除"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/api/admin/docs/category/create")
    public ResponseEntity<Object> createCategory(@RequestBody CategoryBody body,
                                                 HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        try {
            String dir = docsService.createCategory(body.name());
            return ResponseEntity.ok(ApiResponse.success("分类已创建: " + dir, Map.of("dirName", dir)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/api/admin/docs/category/delete")
    public ResponseEntity<Object> deleteCategory(@RequestBody CategoryBody body,
                                                 HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        try {
            docsService.deleteCategory(body.dirName() != null ? body.dirName() : body.name());
            return ResponseEntity.ok(ApiResponse.success("分类已删除"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    @PostMapping("/api/admin/docs/reorder")
    public ResponseEntity<Object> reorder(@RequestBody ReorderBody body, HttpServletRequest request) {
        if (!admin(request)) {
            return forbidden();
        }
        try {
            docsService.reorderCategories(body.items());
            return ResponseEntity.ok(ApiResponse.success("已重排序"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.failure(e.getMessage()));
        }
    }

    private boolean admin(HttpServletRequest request) {
        return AuthUtil.currentUser(request) != null && AuthUtil.isAdmin(request);
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.failure("需要管理员权限"));
    }
}
