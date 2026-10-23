package com.shopping.common.constant;

/**
 * Redis key 统一命名。
 * <p>
 * 规范（见 {@code docs/03-系统架构设计.md} §5）：
 * <pre>shopping:{模块}:{业务}:{标识}</pre>
 * <p>
 * 为什么要集中管理而不是各处字符串拼接：
 * <ul>
 *   <li>避免拼写错误导致「写进去的 key 和读的 key 不一样」——这类 bug 极难排查，
 *       因为 Redis 里看起来「数据就是没了」；</li>
 *   <li>需要按前缀清理（如清空某模块缓存）时，能确定前缀是什么；</li>
 *   <li>面试时能直接说清整个项目的缓存结构。</li>
 * </ul>
 * ⚠️ 生产环境禁用 {@code KEYS shopping:*} 做前缀扫描（会阻塞 Redis），
 * 要用 {@code SCAN}。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    private static final String P = "shopping:";

    /* ---------------- 认证授权 ---------------- */

    /**
     * refreshToken 的存放位置：Hash 结构，key = scope + 用户ID，field = token 的 jti，value = 签发时间。
     * <p>
     * 用 Hash 而不是「一个 jti 一个 key」的好处：<b>可以一次性吊销某用户的所有会话</b>
     * （直接 DEL 整个 key）。用户改密码后强制所有设备下线，就靠这个。
     * <p>
     * <b>为什么 key 里要带 scope：</b>后台用户（{@code sys_user}）与 C 端会员（{@code ums_user}）
     * 是两张表。虽然当前都用雪花 ID 不会撞号，但把 scope 写进 key 是<b>零成本的隔离</b>——
     * 万一以后某张表改成自增主键，也不会出现「后台用户 1 和会员 1 共用同一个 refresh 会话」。
     */
    public static String authRefresh(String userScope, Long userId) {
        return P + "auth:refresh:" + userScope + ":" + userId;
    }

    /**
     * accessToken 黑名单：登出后把该 token 的 jti 放进来，TTL 设为 token 的剩余有效期。
     * <p>
     * TTL 必须等于剩余有效期，不能是固定值——否则黑名单会无限膨胀
     * （已过期的 token 本来自动失效，不需要再记）。
     */
    public static String authBlacklist(String jti) {
        return P + "auth:blacklist:" + jti;
    }

    /**
     * C 端登录态缓存（Hash）：<b>压测优化的产物</b>。
     * <p>没有它时，认证过滤器每个请求都要查一次 {@code ums_user}，
     * 740 QPS 就是 740 次/秒的主键查询，数据库成了吞吐天花板。
     * <p>TTL 60 秒：牺牲「最多 1 分钟的实时性」换掉这条查询，
     * 对秒杀这类场景完全可接受（禁用用户晚 1 分钟不影响资损）。
     */
    public static String authAppUser(Long userId) {
        return P + "auth:appuser:" + userId;
    }

    /* ==================== 秒杀（Phase 3） ==================== */

    /**
     * 秒杀商品的实时可售库存：{@code String} 类型，值是整数。
     * <p>活动预热时写入 {@code seckill_stock - seckill_sold}；抢购时用 <b>Lua 脚本原子 DECR</b>。
     * <p><b>为什么库存放 Redis 而不是直接扣数据库：</b>
     * 秒杀的瞬时并发是「几千请求抢几十件」，如果每个请求都去
     * {@code UPDATE ... WHERE available >= n}，数据库行锁会让请求全部排队，
     * 响应时间从毫秒级涨到秒级，前端全部超时。<b>Redis 单线程 + Lua 原子性
     * 天然就是这个场景的正确工具。</b>
     * <p>Key 里用 {@code seckillSkuId}（而不是 skuId）：同一个 SKU 可以出现在
     * 多个活动里，库存必须按「活动 + SKU」隔离。
     */
    public static String seckillStock(Long seckillSkuId) {
        return P + "seckill:stock:" + seckillSkuId;
    }

    /**
     * 「一人一单」标记：{@code String} 类型，存在即表示该用户已参与过该活动。
     * <p>与 Lua 脚本里的库存扣减<b>在同一个原子操作内</b>完成，
     * 所以不存在「扣了库存但没记下用户」的中间态。
     * <p>这里只是<b>第一道</b>拦截（挡住重复流量、省掉后续写库）；
     * 真正的强约束是 {@code sms_seckill_record} 上的唯一索引
     * {@code uk_activity_id_user_id} —— <b>Redis 会丢数据，唯一索引不会。</b>
     */
    public static String seckillUser(Long activityId, Long userId) {
        return P + "seckill:user:" + activityId + ":" + userId;
    }
}
