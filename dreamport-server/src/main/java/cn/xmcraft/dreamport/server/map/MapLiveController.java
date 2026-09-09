package cn.xmcraft.dreamport.server.map;

import cn.xmcraft.dreamport.server.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 地图在线玩家（公开，配合 /map 页侧栏）：
 * ?i= 地图下标（对应 portal.config 地图列表），URL 只取后台已配置条目。
 */
@RestController
@RequestMapping("/api/map")
public class MapLiveController {

    private final MapProxyService mapProxyService;

    public MapLiveController(MapProxyService mapProxyService) {
        this.mapProxyService = mapProxyService;
    }

    @GetMapping("/live")
    public Map<String, Object> live(@RequestParam(defaultValue = "0") int i) {
        return ApiResponse.success(null, mapProxyService.livePlayers(i));
    }
}
