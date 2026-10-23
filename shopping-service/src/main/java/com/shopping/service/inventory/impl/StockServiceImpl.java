package com.shopping.service.inventory.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shopping.common.constant.CommonConstants;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.WmsStockLogMapper;
import com.shopping.dao.mapper.WmsStockMapper;
import com.shopping.model.entity.WmsStock;
import com.shopping.model.entity.WmsStockLog;
import com.shopping.service.auth.security.LoginUser;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.inventory.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存服务实现（Phase 1 版本）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    /** Phase 1-3 是单仓，固定仓库 ID */
    private static final long DEFAULT_WAREHOUSE_ID = 1L;

    private final WmsStockMapper stockMapper;
    private final WmsStockLogMapper stockLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initStock(Long skuId, int stock, String remark) {
        WmsStock existing = findStock(skuId);
        if (existing != null) {
            // 已有记录（比如编辑时重复保存）：退化成调整，保证幂等且不留重复行
            adjustStock(skuId, stock, remark);
            return;
        }
        WmsStock ws = new WmsStock();
        ws.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        ws.setSkuId(skuId);
        ws.setWarehouseId(DEFAULT_WAREHOUSE_ID);
        ws.setStock(stock);
        ws.setLockedStock(0);
        ws.setWarnStock(0);
        // 期初库存计入累计入库，否则对账时「累计入 - 累计出 ≠ 当前库存」
        ws.setTotalIn(stock);
        ws.setTotalOut(0);
        ws.setVersion(0);
        // ⚠️ 绝不 set availableStock —— 它是数据库生成列，写入会报 ERROR 3105
        stockMapper.insert(ws);

        writeLog(skuId, WmsStockLog.BIZ_STOCK_IN, "IN_" + ws.getId(), null, stock, 0, stock, remark);
        log.info("初始化库存 | skuId={} | stock={}", skuId, stock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustStock(Long skuId, int newStock, String remark) {
        if (newStock < 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存不能为负数");
        }
        WmsStock ws = findStock(skuId);
        if (ws == null) {
            initStock(skuId, newStock, remark);
            return;
        }
        int before = nz(ws.getStock());
        int locked = nz(ws.getLockedStock());

        // 已锁定的部分不能被调走：否则会出现「可售库存为负」，
        // 而 available_stock 是生成列，负值会被 CHECK 约束直接拒绝（ERROR 3819），
        // 与其让数据库报一个难懂的错，不如在应用层给出清晰提示。
        if (newStock < locked) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "新库存(" + newStock + ")不能小于已锁定库存(" + locked + ")");
        }
        int delta = newStock - before;
        if (delta == 0) {
            log.debug("库存无变化，跳过 | skuId={} | stock={}", skuId, newStock);
            return;
        }

        WmsStock update = new WmsStock();
        update.setId(ws.getId());
        update.setStock(newStock);
        // 维护累计出/入库，保证「累计入 - 累计出 == 当前库存」这条对账口径成立
        if (delta > 0) {
            update.setTotalIn(nz(ws.getTotalIn()) + delta);
        } else {
            update.setTotalOut(nz(ws.getTotalOut()) - delta);
        }
        stockMapper.updateById(update);

        writeLog(skuId, WmsStockLog.BIZ_ADJUST, "ADJ_" + IdWorker.getId(), null, delta,
                before - locked, newStock - locked, remark);
        log.info("后台调整库存 | skuId={} | {} -> {} | delta={}", skuId, before, newStock, delta);
    }

    @Override
    public int getAvailableStock(Long skuId) {
        WmsStock ws = findStock(skuId);
        if (ws == null) {
            return 0;
        }
        // available_stock 是生成列，直接读库里的值，不要在应用层再算一遍
        // （应用层算与 DB 算不一致时，以 DB 为准才是对的）
        return availableOf(ws);
    }

    @Override
    public Map<Long, Integer> getAvailableStocks(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        // 一次 IN 查询拿完，避免调用方逐个查造成 N+1
        return stockMapper.selectList(Wrappers.<WmsStock>lambdaQuery()
                        .in(WmsStock::getSkuId, skuIds)
                        .eq(WmsStock::getWarehouseId, DEFAULT_WAREHOUSE_ID))
                .stream()
                .collect(Collectors.toMap(WmsStock::getSkuId, StockServiceImpl::availableOf, (a, b) -> a));
    }

    /* ==================== Phase 2：交易的四个原子操作 ==================== */

    /**
     * 下单锁定。
     * <p>先读一次「变动前可售」纯粹是为了写流水的 before_stock ——
     * <b>但判断能不能锁，绝不能依赖这次读到的值</b>，必须由下面那条条件更新的
     * 受影响行数决定。这就是「先查后改」与「条件更新」的区别。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockStock(Long skuId, int num, String orderNo) {
        requirePositive(num);
        int availableBefore = currentAvailable(skuId);

        int affected = stockMapper.lockStock(skuId, DEFAULT_WAREHOUSE_ID, num);
        if (affected == 0) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH,
                    "库存不足（可售 " + Math.max(availableBefore, 0) + "，需要 " + num + "）");
        }
        // 锁定占用可售 → change_num 为负
        writeLog(skuId, WmsStockLog.BIZ_LOCK, orderNo, orderNo, -num,
                availableBefore, availableBefore - num, "下单锁定");
        log.info("锁定库存 | skuId={} | num={} | orderNo={} | 可售 {} -> {}",
                skuId, num, orderNo, availableBefore, availableBefore - num);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(Long skuId, int num, String orderNo, String paymentNo) {
        requirePositive(num);
        int availableBefore = currentAvailable(skuId);

        int affected = stockMapper.deductStock(skuId, DEFAULT_WAREHOUSE_ID, num);
        if (affected == 0) {
            throw new BizException(ResultCode.STOCK_DEDUCT_FAILED,
                    "冻结库存不足，无法扣减（skuId=" + skuId + "，需要 " + num + "）");
        }
        // stock 与 locked_stock 同减 → 可售库存不变 → change_num = 0。
        // 面试点：为什么扣减流水是 0？因为可售在【锁定那一刻】就已经让出去了，
        // 支付只是把「冻结」转成「实扣」，对可售没有净影响。
        writeLog(skuId, WmsStockLog.BIZ_DEDUCT, paymentNo, orderNo, 0,
                availableBefore, availableBefore, "支付扣减");
        log.info("扣减库存 | skuId={} | num={} | orderNo={} | paymentNo={} | 可售保持 {}",
                skuId, num, orderNo, paymentNo, availableBefore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(Long skuId, int num, String orderNo) {
        requirePositive(num);
        int availableBefore = currentAvailable(skuId);

        int affected = stockMapper.releaseStock(skuId, DEFAULT_WAREHOUSE_ID, num);
        if (affected == 0) {
            // 没锁过这么多却要释放 → 调用方逻辑有问题，必须让它暴露而不是静默吞掉。
            // 静默的后果是「释放了别人的锁定」，直接导致别人超卖。
            throw new BizException(ResultCode.STOCK_DEDUCT_FAILED,
                    "冻结库存不足，无法释放（skuId=" + skuId + "，需要 " + num + "）");
        }
        // biz_no 用 orderNo + "_CANCEL"：与「锁定」区分开，且同一订单只能取消一次
        writeLog(skuId, WmsStockLog.BIZ_RELEASE, orderNo + CANCEL_SUFFIX, orderNo, num,
                availableBefore, availableBefore + num, "取消/超时释放");
        log.info("释放库存 | skuId={} | num={} | orderNo={} | 可售 {} -> {}",
                skuId, num, orderNo, availableBefore, availableBefore + num);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundStock(Long skuId, int num, String orderNo, String refundNo) {
        requirePositive(num);
        int availableBefore = currentAvailable(skuId);

        int affected = stockMapper.refundStock(skuId, DEFAULT_WAREHOUSE_ID, num);
        if (affected == 0) {
            throw new BizException(ResultCode.STOCK_DEDUCT_FAILED,
                    "回补库存失败：库存记录不存在（skuId=" + skuId + "）");
        }
        writeLog(skuId, WmsStockLog.BIZ_REFUND, refundNo, orderNo, num,
                availableBefore, availableBefore + num, "退款回补");
        log.info("回补库存 | skuId={} | num={} | orderNo={} | refundNo={}",
                skuId, num, orderNo, refundNo);
    }

    /* ---------------- 内部方法 ---------------- */

    /** 取消释放流水的 biz_no 后缀，与「锁定」区分开 */
    private static final String CANCEL_SUFFIX = "_CANCEL";

    /** 当前可售库存；无记录返回 0（后续的条件更新会失败并抛出业务异常） */
    private int currentAvailable(Long skuId) {
        WmsStock ws = findStock(skuId);
        return ws == null ? 0 : availableOf(ws);
    }

    /**
     * 可售库存：优先读数据库的生成列。
     * <p>生成列由 DB 维护，是唯一权威；应用层只在它为 null（理论上不会）时才自己算。
     */
    private static int availableOf(WmsStock ws) {
        return ws.getAvailableStock() == null
                ? nz(ws.getStock()) - nz(ws.getLockedStock())
                : ws.getAvailableStock();
    }

    private static void requirePositive(int num) {
        if (num <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "库存变动数量必须大于 0，当前为 " + num);
        }
    }

    private WmsStock findStock(Long skuId) {
        return stockMapper.selectOne(Wrappers.<WmsStock>lambdaQuery()
                .eq(WmsStock::getSkuId, skuId)
                .eq(WmsStock::getWarehouseId, DEFAULT_WAREHOUSE_ID));
    }

    /**
     * 写一条库存流水。
     * <p>{@code beforeStock} / {@code afterStock} 记的都是<b>可售库存</b>（stock - locked），
     * 这样对账时「流水汇总」能直接与 {@code available_stock} 对上。
     */
    private void writeLog(Long skuId, int bizType, String bizNo, String orderNo, int changeNum,
                          int beforeAvailable, int afterAvailable, String remark) {
        WmsStockLog entity = new WmsStockLog();
        entity.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        entity.setSkuId(skuId);
        entity.setWarehouseId(DEFAULT_WAREHOUSE_ID);
        entity.setBizType(bizType);
        entity.setBizNo(bizNo);
        entity.setOrderNo(orderNo);
        entity.setChangeNum(changeNum);
        entity.setBeforeStock(beforeAvailable);
        entity.setAfterStock(afterAvailable);
        entity.setRemark(remark);

        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser == null) {
            // 定时任务、MQ 消费者等无登录上下文的场景
            entity.setOperatorType(WmsStockLog.OPERATOR_SYSTEM);
        } else {
            entity.setOperatorType(WmsStockLog.OPERATOR_ADMIN);
            entity.setOperatorId(loginUser.getUserId());
        }
        stockLogMapper.insert(entity);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
