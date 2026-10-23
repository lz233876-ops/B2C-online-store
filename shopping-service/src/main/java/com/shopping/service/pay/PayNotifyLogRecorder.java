package com.shopping.service.pay;

import com.shopping.dao.mapper.PayNotifyLogMapper;
import com.shopping.model.entity.PayNotifyLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 回调日志记录器 —— <b>刻意独立成一个 Bean、独立事务</b>。
 * <p>
 * <b>为什么要 {@code REQUIRES_NEW}：</b>
 * 回调处理业务失败时，外层事务会回滚。如果日志写在外层事务里，
 * 它<b>会跟着一起消失</b>——而排查线上问题时，最需要的恰恰就是那条失败的回调记录。
 * 「先留痕、再处理」的价值全在于日志能存活。
 * <p>
 * <b>为什么必须独立成 Bean：</b>Spring 的 {@code @Transactional} 靠代理生效，
 * 同类内部方法自调用会绕过代理，{@code REQUIRES_NEW} 静默失效——
 * 这是个不报错、只表现为「日志偶尔丢失」的隐蔽 bug。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayNotifyLogRecorder {

    /** 验签结果 */
    public static final int VERIFY_NOT_YET = 0;
    public static final int VERIFY_PASS = 1;
    public static final int VERIFY_FAIL = 2;

    /** 处理结果 */
    public static final int HANDLE_PENDING = 0;
    public static final int HANDLE_SUCCESS = 1;
    public static final int HANDLE_FAIL = 2;
    public static final int HANDLE_DUPLICATE = 3;

    private final PayNotifyLogMapper notifyLogMapper;

    /**
     * 记录一条收到的通知。
     *
     * @return 日志行 ID；<b>返回 {@code null} 表示这条通知之前已经收到过</b>
     *         （唯一键 {@code (channel, channel_notify_id)} 冲突）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long record(PayNotifyLog entity) {
        try {
            entity.setVerifyStatus(VERIFY_NOT_YET);
            entity.setHandleStatus(HANDLE_PENDING);
            notifyLogMapper.insert(entity);
            return entity.getId();
        } catch (DuplicateKeyException e) {
            // 同一条通知（同渠道 + 同 notify_id）被原样重投。
            // 注意：这【只】能识别字面重复；渠道换 notify_id 重投时这里不会命中，
            // 那种情况要靠支付单的条件更新兜底。
            log.info("重复的通知（同 notify_id），跳过处理 | channel={} | notifyId={}",
                    entity.getChannel(), entity.getChannelNotifyId());
            return null;
        }
    }

    /** 更新处理结果。同样用独立事务，保证即便外层业务回滚，处理痕迹也能留下 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finish(Long logId, int verifyStatus, int handleStatus, String handleMsg, long costTime) {
        if (logId == null) {
            return;
        }
        try {
            PayNotifyLog update = new PayNotifyLog();
            update.setId(logId);
            update.setVerifyStatus(verifyStatus);
            update.setHandleStatus(handleStatus);
            update.setHandleMsg(truncate(handleMsg));
            update.setHandleTime(LocalDateTime.now());
            update.setCostTime(costTime);
            notifyLogMapper.updateById(update);
        } catch (Exception e) {
            // 日志更新失败绝不能影响回调处理结果
            log.error("更新回调日志失败 | logId={}", logId, e);
        }
    }

    /** handle_msg 是 varchar(500)，超长会直接报错，这里先截断 */
    private static String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }
}
