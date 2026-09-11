package cn.xmcraft.dreamport.plugin.internal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Essentials userdata 家位置解析单测(样例 JSON,不依赖服务端)。
 */
class LocationReporterTest {

    private static JsonObject userdata(String json) {
        return new Gson().fromJson(json, JsonObject.class);
    }

    @Test
    void 解析多家与坐标() {
        var homes = LocationReporter.parseHomes(userdata("""
                {"homes":{
                  "home":{"world":"world","x":1.5,"y":64.0,"z":-3.2},
                  "矿场":{"world":"world_nether","x":100.0,"y":32.5,"z":-200.75}
                }}"""));
        assertEquals(2, homes.size());
        assertEquals("home", homes.get(0).name());
        assertEquals("world", homes.get(0).world());
        assertEquals(1.5, homes.get(0).x());
        assertEquals(-3.2, homes.get(0).z());
        assertEquals("矿场", homes.get(1).name());
    }

    @Test
    void 无家或畸形数据返回空() {
        assertTrue(LocationReporter.parseHomes(userdata("{}")).isEmpty(), "无 homes 键 → 空");
        assertTrue(LocationReporter.parseHomes(userdata("{\"homes\":{}}")).isEmpty(), "空 homes → 空");
        assertTrue(LocationReporter.parseHomes(null).isEmpty());
        // 单条畸形(缺 world)跳过,不影响其它条目
        var mixed = LocationReporter.parseHomes(userdata("""
                {"homes":{
                  "bad":{"x":1.0},
                  "good":{"world":"world","x":2.0,"y":3.0,"z":4.0}
                }}"""));
        assertEquals(1, mixed.size());
        assertEquals("good", mixed.get(0).name());
    }

    @Test
    void 文件缺失或样例内容_解析安全(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        var reporter = new LocationReporter(null);
        assertTrue(reporter.readHomes(UUID.randomUUID()).isEmpty(), "userdata 文件不存在 → 空列表(不抛错)");
        // EssentialsX 真实样例结构(含 money 等无关键):homes 正确抽出
        Path file = dir.resolve("069a79f4-44e9-4726-a5be-fca90e38aaf5.json");
        Files.writeString(file, """
                {"homes":{"home":{"world":"world","x":10.0,"y":65.0,"z":20.0}},"money":50.0}""");
        var parsed = LocationReporter.parseHomes(new Gson().fromJson(Files.readString(file), JsonObject.class));
        assertEquals(1, parsed.size());
        assertEquals("home", parsed.get(0).name());
        assertEquals(65.0, parsed.get(0).y());
    }
}
