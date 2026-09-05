package cn.xmcraft.dreamport.server.bootstrap;

import cn.xmcraft.dreamport.server.security.PasswordService;
import cn.xmcraft.dreamport.server.user.UserRecord;
import cn.xmcraft.dreamport.server.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 演示账号播种（wl.seed-demo=true 时执行，生产必须关闭）：
 * - demo/demo12345：以【旧版 $SHA$ 格式】入库 → 验证"老密码无缝登录 + 透明升级 bcrypt"
 * - demo2/demo12345：bcrypt 直接入库，pending_review 状态
 */
@Component
public class DevSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final boolean seedDemo;

    public DevSeeder(UserRepository userRepository, PasswordService passwordService,
                     org.springframework.core.env.Environment env) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.seedDemo = Boolean.parseBoolean(env.getProperty("wl.seed-demo", "true"));
    }

    @Override
    public void run(String... args) {
        if (!seedDemo || userRepository.findByUsernameIgnoreCase("demo").isPresent()) {
            return;
        }
        UserRecord demo = new UserRecord(null, "demo", "demo@xmcraft.cn", "approved",
                "legacy_sha256", passwordService.legacyHash("demo12345"), null,
                null, null, null, null, null, null, null, null, null,
                null, "Demo", null, null, null, null, null, null, null, null,
                null, null, null);
        userRepository.save(demo);

        UserRecord demo2 = new UserRecord(null, "demo2", "demo2@xmcraft.cn", "pending_review",
                "bcrypt", passwordService.hash("demo12345"), null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null);
        userRepository.save(demo2);

        log.info("[DevSeeder] 已播种演示账号：demo/demo12345（legacy_sha256，验证旧哈希兼容）与 demo2/demo12345（bcrypt）");
    }
}
