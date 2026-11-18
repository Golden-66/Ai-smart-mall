package com.aimall.user;

import com.aimall.common.dto.Result;
import com.aimall.common.util.JwtUtil;
import com.aimall.common.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/users")
public class UserController {

    public record RegisterReq(String username, String password, String nickname) {
    }

    public record LoginReq(String username, String password) {
    }

    public record LoginResp(String token, long userId, String username, long expiresIn) {
    }

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    private final UserRepository repo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String secret;
    private final long ttlSeconds;

    public UserController(UserRepository repo,
                          @Value("${mall.jwt.secret}") String secret,
                          @Value("${mall.jwt.ttl-seconds:86400}") long ttlSeconds) {
        this.repo = repo;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterReq req) {
        if (req.username() == null || !USERNAME.matcher(req.username()).matches()) {
            throw new BizException("用户名需为 3-32 位字母、数字或下划线");
        }
        if (req.password() == null || req.password().length() < 6 || req.password().length() > 64) {
            throw new BizException("密码长度需为 6-64 位");
        }
        try {
            long id = repo.insert(req.username(), encoder.encode(req.password()),
                    req.nickname() == null || req.nickname().isBlank() ? req.username() : req.nickname());
            return Result.ok(Map.of("userId", id, "username", req.username()));
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "用户名已存在");
        }
    }

    @PostMapping("/login")
    public Result<LoginResp> login(@RequestBody LoginReq req) {
        var user = repo.findByUsername(req.username() == null ? "" : req.username())
                .filter(u -> req.password() != null && encoder.matches(req.password(), u.passwordHash()))
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        String token = JwtUtil.issue(secret, user.id(), user.username(), ttlSeconds);
        return Result.ok(new LoginResp(token, user.id(), user.username(), ttlSeconds));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader("X-User-Id") long userId) {
        var user = repo.findById(userId).orElseThrow(() -> new BizException(404, "用户不存在"));
        return Result.ok(Map.of("userId", user.id(), "username", user.username(), "nickname", user.nickname()));
    }
}
