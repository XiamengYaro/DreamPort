package cn.xmcraft.dreamport.server.docs;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文档中心（文件系统 docs/ 目录，分类=数字前缀子目录，对齐旧版 DocsHandler/DocsManagerHandler）。
 * 安全：路径穿越防护（canonical path 校验 + 文件名白名单）。
 */
@Service
public class DocsService {

    private static final Pattern SAFE_NAME = Pattern.compile("^[\\w\\u4e00-\\u9fa5.-]{1,80}$");
    private static final Pattern CATEGORY_PREFIX = Pattern.compile("^\\d{2}-");

    private final Path root = Path.of("docs");

    public DocsService() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("docs 目录创建失败", e);
        }
    }

    public record DocFile(String filename, String title, String category) {
    }

    public record DocContent(String filename, String title, String category, String content) {
    }

    /** 全量列表：分类目录 + 未分类文件 */
    public Map<String, Object> listAll() {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> categories = new ArrayList<>();
        List<String> uncategorized = new ArrayList<>();
        try (var stream = Files.list(root)) {
            List<Path> entries = stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    List<String> files = listMd(entry);
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", name);
                    cat.put("displayName", name.replaceFirst(CATEGORY_PREFIX.pattern(), ""));
                    cat.put("files", files);
                    categories.add(cat);
                } else if (name.endsWith(".md")) {
                    uncategorized.add(name);
                }
            }
        } catch (IOException e) {
            // 返回空结构
        }
        body.put("categories", categories);
        body.put("uncategorized", uncategorized);
        return body;
    }

    private List<String> listMd(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md"))
                    .sorted()
                    .toList();
        }
    }

    /** 读取文档内容（category 可空；filename 允许带分类路径 01-规则/index.md） */
    public DocContent read(String category, String filename) throws IOException {
        Path path = resolve(category, filename);
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String title = filename.endsWith(".md")
                ? filename.substring(0, filename.length() - 3) : filename;
        return new DocContent(filename, title, category, content);
    }

    /** 新建文档：标题转文件名（非法字符→_），category 可空 */
    public DocContent create(String title, String category, String content) throws IOException {
        if (title == null || !SAFE_NAME.matcher(sanitize(title)).matches()) {
            throw new IllegalArgumentException("标题不合法");
        }
        String filename = sanitize(title) + ".md";
        Path dir = category == null || category.isBlank() ? root : categoryDir(category);
        Path path = dir.resolve(filename);
        if (Files.exists(path)) {
            throw new IllegalArgumentException("文档已存在");
        }
        String initial = content != null && !content.isBlank() ? content
                : "# " + title + "\n\n在此撰写文档内容（Markdown）。\n";
        Files.writeString(path, initial, StandardCharsets.UTF_8);
        return new DocContent(filename, title, category, initial);
    }

    public void update(String category, String filename, String content) throws IOException {
        Path path = resolve(category, filename);
        Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public void delete(String category, String filename) throws IOException {
        Files.deleteIfExists(resolve(category, filename));
    }

    public String createCategory(String name) throws IOException {
        if (name == null || !SAFE_NAME.matcher(sanitize(name)).matches()) {
            throw new IllegalArgumentException("分类名不合法");
        }
        int max = 0;
        try (var stream = Files.list(root)) {
            for (Path p : stream.filter(Files::isDirectory).toList()) {
                String n = p.getFileName().toString();
                if (CATEGORY_PREFIX.matcher(n).matches()) {
                    try {
                        max = Math.max(max, Integer.parseInt(n.substring(0, 2)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        String dirName = String.format("%02d-%s", max + 1, sanitize(name));
        Files.createDirectory(root.resolve(dirName));
        return dirName;
    }

    public void deleteCategory(String category) throws IOException {
        Path dir = categoryDir(category);
        try (var stream = Files.list(dir)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
        Files.deleteIfExists(dir);
    }

    /** 按给定顺序重编号分类目录：01-xx、02-xx… */
    public void reorderCategories(List<String> orderedNames) throws IOException {
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        }
        int seq = 1;
        for (String name : orderedNames) {
            for (Path dir : dirs) {
                String n = dir.getFileName().toString();
                if (n.equals(name) || n.replaceFirst(CATEGORY_PREFIX.pattern(), "").equals(name)) {
                    String target = String.format("%02d-%s", seq++,
                            n.replaceFirst(CATEGORY_PREFIX.pattern(), ""));
                    if (!n.equals(target)) {
                        Files.move(dir, dir.getParent().resolve(target));
                    }
                }
            }
        }
    }

    /** 路径解析 + 防穿越（Rules.md 安全要求） */
    private Path resolve(String category, String filename) throws IOException {
        if (filename == null || !SAFE_NAME.matcher(filename).matches()) {
            throw new IOException("文件名不合法");
        }
        Path base = category == null || category.isBlank() ? root : categoryDir(category);
        Path path = base.resolve(filename).normalize();
        if (!path.startsWith(root.toAbsolutePath().normalize())) {
            throw new IOException("非法路径");
        }
        return path;
    }

    private Path categoryDir(String category) throws IOException {
        if (!SAFE_NAME.matcher(category).matches()) {
            throw new IOException("分类名不合法");
        }
        Path dir = root.resolve(category);
        if (!Files.isDirectory(dir) || !dir.normalize().startsWith(root)) {
            throw new IOException("分类不存在");
        }
        return dir;
    }

    private String sanitize(String name) {
        return name.replaceAll("[^\\w\\u4e00-\\u9fa5.-]", "_");
    }
}
