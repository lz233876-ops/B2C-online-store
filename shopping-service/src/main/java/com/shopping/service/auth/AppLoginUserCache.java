package com.shopping.service.auth;

import com.shopping.common.constant.RedisKeys;
import com.shopping.model.entity.UmsUser;
import com.shopping.service.auth.security.AppLoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * C 端登录主体的缓存层（<b>压测驱动出来的优化</b>）。
 * <p>
 * <b>为什么需要它：</b>秒杀压测（1000 请求 / 200 并发）打到 740 QPS 时，
 * P50 竟然有 208ms。<b>而抢购接口本身只碰 Redis、不碰数据库</b> —— 瓶颈不在业务逻辑，
 * 而在认证过滤器 {@link com.shopping.web.config.security.AppJwtAuthenticationFilter}：
 * 它每个请求都要 {@code SELECT * FROM ums_user WHERE id = ?} 来还原登录态。
 * 740 QPS 就是 740 次/秒的单表主键查询，数据库成了整个系统吞吐的天花板。
 * <p>
 * <b>缓存什么：</b>只缓存还原登录态必需的少量字段（不缓存整个实体，
 * 避免过期字段与库不一致时出现「缓存里是旧昵称」这类问题）。
 * <p>
 * <b>一致性代价（必须说清楚）：</b>TTL 60 秒意味着「管理员禁用某会员 / 会员改昵称」
 * 最多 60 秒后才在接口层生效。<b>这对秒杀完全可接受</b>（禁用一个用户晚 1 分钟
 * 不影响资损），但如果是「扣款权限」这类东西就不能这么缓存。
 * 需要立刻生效时，在对应的写操作里主动删掉缓存键即可（{@link #evict}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppLoginUserCache implements AppLoginUserService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private static final String F_PHONE = "phone";
    private static final String F_NICKNAME = "nickname";
    private static final String F_AVATAR = "avatar";
    private static final String F_STATUS = "status";

    private final StringRedisTemplate redis;
    private final UmsUserDetailsService dbLoader;

    @Override
    public AppLoginUser loadByUserId(Long userId) {
        String key = RedisKeys.authAppUser(userId);
        Map<Object, Object> cached;
        try {
            cached = redis.opsForHash().entries(key);
        } catch (Exception e) {
            // Redis 故障时降级查库：认证是主链路，不能因为缓存挂了就全站 401
            log.warn("读取登录态缓存失败，降级查库 | userId={}", userId, e);
            return dbLoader.loadByUserId(userId);
        }
        if (cached == null || cached.isEmpty()) {
            AppLoginUser user = dbLoader.loadByUserId(userId);
            cache(user);
            return user;
        }
        // 用缓存字段重建一个登录主体（password 传 null：JWT 已验签，此处不需要密码）
        UmsUser stub = new UmsUser();
        stub.setId(userId);
        stub.setPhone((String) cached.get(F_PHONE));
        stub.setNickname((String) cached.get(F_NICKNAME));
        stub.setAvatar((String) cached.get(F_AVATAR));
        Object status = cached.get(F_STATUS);
        stub.setStatus(status == null ? 1 : Integer.parseInt(String.valueOf(status)));
        return new AppLoginUser(stub);
    }

    /** 主动失效（改昵称、禁用、改手机号后调用） */
    public void evict(Long userId) {
        try {
            redis.delete(RedisKeys.authAppUser(userId));
        } catch (Exception e) {
            log.warn("清除登录态缓存失败 | userId={}", userId, e);
        }
    }

    private void cache(AppLoginUser user) {
        try {
            String key = RedisKeys.authAppUser(user.getUserId());
            Map<String, String> fields = Map.of(
                    F_PHONE, user.getPhone() == null ? "" : user.getPhone(),
                    F_NICKNAME, user.getNickname() == null ? "" : user.getNickname(),
                    F_AVATAR, user.getAvatar() == null ? "" : user.getAvatar(),
                    F_STATUS, String.valueOf(user.getStatus() == null ? 1 : user.getStatus()));
            redis.opsForHash().putAll(key, fields);
            redis.expire(key, TTL);
        } catch (Exception e) {
            // 写缓存失败不影响本次请求
            log.warn("写入登录态缓存失败 | userId={}", user.getUserId(), e);
        }
    }
}
