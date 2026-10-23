package com.shopping.service.aftersale.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.common.constant.CommonConstants;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.PageResult;
import com.shopping.common.result.ResultCode;
import com.shopping.dao.mapper.OmsAftersaleMapper;
import com.shopping.dao.mapper.OmsOrderItemMapper;
import com.shopping.dao.mapper.OmsOrderMapper;
import com.shopping.dao.mapper.OmsOrderOperateHistoryMapper;
import com.shopping.dao.mapper.PayPaymentMapper;
import com.shopping.dao.mapper.PayRefundMapper;
import com.shopping.model.dto.AftersaleApplyDTO;
import com.shopping.model.dto.AftersaleAuditDTO;
import com.shopping.model.dto.AftersaleReturnDTO;
import com.shopping.model.entity.OmsAftersale;
import com.shopping.model.entity.OmsOrder;
import com.shopping.model.entity.OmsOrderItem;
import com.shopping.model.entity.OmsOrderOperateHistory;
import com.shopping.model.entity.PayPayment;
import com.shopping.model.entity.PayRefund;
import com.shopping.model.enums.AftersaleStatus;
import com.shopping.model.enums.AftersaleType;
import com.shopping.model.enums.OrderStatus;
import com.shopping.model.enums.PaymentStatus;
import com.shopping.model.vo.AftersaleDetailVO;
import com.shopping.model.vo.AftersaleVO;
import com.shopping.service.aftersale.AftersaleService;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.inventory.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AftersaleServiceImpl implements AftersaleService {

    /** 超时未处理自动关闭：7 天（与表注释一致） */
    private static final int AUTO_CLOSE_DAYS = 7;

    private static final String ACTION_APPLY = "APPLY_AFTER_SALE";
    private static final String ACTION_REFUND = "AFTER_SALE_REFUND";
    private static final String ACTION_REJECT = "AFTER_SALE_REJECT";
    private static final String ACTION_CANCEL = "AFTER_SALE_CANCEL";

    private static final int OPERATOR_USER = 1;
    private static final int OPERATOR_ADMIN = 2;
    private static final int OPERATOR_SYSTEM = 3;

    /** pay_refund.status：0 待退款，1 退款中，2 退款成功，3 退款失败 */
    private static final int REFUND_STATUS_SUCCESS = 2;
    /** pay_refund.refund_type：1 全额，2 部分 */
    private static final int REFUND_TYPE_FULL = 1;
    private static final int REFUND_TYPE_PARTIAL = 2;

    /** oms_order_item.refund_status：0 未退款，1 部分退款，2 全额退款 */
    private static final int ITEM_REFUND_NONE = 0;
    private static final int ITEM_REFUND_PARTIAL = 1;
    private static final int ITEM_REFUND_FULL = 2;

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OmsAftersaleMapper aftersaleMapper;
    private final OmsOrderMapper orderMapper;
    private final OmsOrderItemMapper orderItemMapper;
    private final OmsOrderOperateHistoryMapper historyMapper;
    private final PayPaymentMapper paymentMapper;
    private final PayRefundMapper refundMapper;
    private final StockService stockService;
    private final ObjectMapper objectMapper;

    /* ==================== 用户端 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(AftersaleApplyDTO dto) {
        Long userId = SecurityUtils.getAppUserId();
        AftersaleType type = resolveType(dto.getType());

        OmsOrderItem item = orderItemMapper.selectById(dto.getOrderItemId());
        if (item == null || !Objects.equals(item.getUserId(), userId)) {
            // 不区分「不存在」与「不是你的」：避免攻击者通过错误码探测他人订单明细
            throw new BizException(ResultCode.ORDER_NOT_FOUND, "订单明细不存在");
        }
        OmsOrder order = orderMapper.selectById(item.getOrderId());
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }

        // 只有「待收货」「已完成」能申请售后。没发货就退款走的是「取消订单」，不是售后。
        //
        // ⚠️ 但「售后中(5)」也必须允许申请 —— 这是本项目第一版的一个真实缺口：
        //    把状态检查写成「必须是 2 或 3」之后，一笔订单只要有【一个商品】在售后中，
        //    其它商品就再也申请不了售后了。而售后的粒度是「订单明细」，
        //    订单级状态不该成为商品级操作的门槛。
        //    真正的重复防护是下面的「同明细同类型只能一次」+ 唯一索引。
        OrderStatus status = OrderStatus.of(order.getStatus());
        boolean alreadyInAftersale = status == OrderStatus.AFTER_SALE;
        if (!alreadyInAftersale
                && status != OrderStatus.PENDING_RECEIPT && status != OrderStatus.COMPLETED) {
            throw new BizException(ResultCode.AFTERSALE_NOT_ALLOWED,
                    "订单当前状态为「" + status.getDesc() + "」，不能申请售后");
        }
        if (Objects.equals(item.getRefundStatus(), ITEM_REFUND_FULL)) {
            throw new BizException(ResultCode.AFTERSALE_ALREADY_EXISTS, "该商品已全额退款");
        }

        // 同一明细 + 同一类型只能有一次申请（唯一索引兜底，这里先给友好提示）
        Long exists = aftersaleMapper.selectCount(Wrappers.<OmsAftersale>lambdaQuery()
                .eq(OmsAftersale::getOrderItemId, item.getId())
                .eq(OmsAftersale::getType, type.getCode()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.AFTERSALE_ALREADY_EXISTS);
        }

        // ---- 退款金额校验 ----
        // 上限是「该明细实付 - 已退」，不是订单总额，也不是单价×数量（可能用过优惠券）
        long refundable = nz(item.getPayAmount()) - nz(item.getRefundAmount());
        if (refundable <= 0) {
            throw new BizException(ResultCode.AFTERSALE_AMOUNT_INVALID, "该商品已无可退金额");
        }
        int quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (quantity > nzInt(item.getQuantity())) {
            throw new BizException(ResultCode.AFTERSALE_AMOUNT_INVALID,
                    "退货数量 " + quantity + " 超过购买数量 " + item.getQuantity());
        }
        long refundAmount;
        if (dto.getRefundAmount() == null) {
            // 按实付单价折算：先算单价再乘数量，避免「先乘后除」的溢出与精度问题
            long unitPay = refundable / nzInt(item.getQuantity());
            refundAmount = unitPay * quantity;
        } else {
            refundAmount = dto.getRefundAmount();
        }
        if (refundAmount <= 0 || refundAmount > refundable) {
            throw new BizException(ResultCode.AFTERSALE_AMOUNT_INVALID,
                    "退款金额需在 1 ~ " + refundable + " 分之间，当前 " + refundAmount);
        }

        // ---- 订单 → 售后中（若已经处于售后中则不重复流转）----
        // 条件更新：只有订单当前状态未被改动才成功
        if (!alreadyInAftersale) {
            int orderAffected = orderMapper.updateStatus(order.getId(), status.getCode(),
                    OrderStatus.AFTER_SALE.getCode(),
                    null, null, null, null, null, null, null, null, null, null);
            if (orderAffected == 0) {
                throw new BizException(ResultCode.ORDER_STATUS_CHANGED);
            }
        }

        OmsAftersale entity = new OmsAftersale();
        entity.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        entity.setAftersaleNo(generateAftersaleNo());
        entity.setOrderId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        entity.setOrderItemId(item.getId());
        entity.setUserId(userId);
        entity.setType(type.getCode());
        entity.setStatus(AftersaleStatus.PENDING_AUDIT.getCode());
        entity.setReason(dto.getReason());
        entity.setDescription(dto.getDescription());
        entity.setImages(toJson(dto.getImages()));
        entity.setRefundAmount(refundAmount);
        entity.setActualRefundAmount(0L);
        entity.setQuantity(quantity);
        // 超时自动关闭时间落库（而不是实时算），这样扫描能走索引
        entity.setAutoCloseTime(LocalDateTime.now().plusDays(AUTO_CLOSE_DAYS));
        aftersaleMapper.insert(entity);

        writeOrderHistory(order, ACTION_APPLY, status.getCode(),
                OrderStatus.AFTER_SALE.getCode(), OPERATOR_USER, userId,
                SecurityUtils.getAppLoginUser().getNickname(),
                "申请售后：" + type.getDesc() + "，金额 " + refundAmount + " 分");

        log.info("申请售后 | aftersaleNo={} | orderNo={} | type={} | 金额={}",
                entity.getAftersaleNo(), order.getOrderNo(), type.getDesc(), refundAmount);
        return entity.getId();
    }

    @Override
    public PageResult<AftersaleVO> pageOfCurrentUser(int pageNum, int pageSize, Integer status) {
        return pageAftersales(pageNum, pageSize, status, SecurityUtils.getAppUserId());
    }

    @Override
    public AftersaleDetailVO detailOfCurrentUser(Long id) {
        return buildDetail(requireOwnedAftersale(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByUser(Long id) {
        OmsAftersale aftersale = requireOwnedAftersale(id);
        if (!Objects.equals(aftersale.getStatus(), AftersaleStatus.PENDING_AUDIT.getCode())) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL,
                    "只能在「待审核」阶段撤销，当前为「" + statusDesc(aftersale.getStatus()) + "」");
        }
        transition(aftersale, AftersaleStatus.PENDING_AUDIT, AftersaleStatus.CLOSED,
                null, null, "用户主动撤销", null, LocalDateTime.now());
        revertOrder(aftersale, ACTION_CANCEL, "用户撤销售后申请");
        log.info("用户撤销售后 | aftersaleNo={}", aftersale.getAftersaleNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReturn(Long id, AftersaleReturnDTO dto) {
        OmsAftersale aftersale = requireOwnedAftersale(id);
        if (!Objects.equals(aftersale.getStatus(), AftersaleStatus.RETURNING.getCode())) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL,
                    "当前状态为「" + statusDesc(aftersale.getStatus()) + "」，不能填写退货物流");
        }
        // 状态不变（仍是「退货中」），只补物流信息。
        // 条件里带上 status 依然重要：防止并发下把已收货/已关闭的单子改了
        OmsAftersale update = new OmsAftersale();
        update.setReturnCompany(dto.getReturnCompany());
        update.setReturnNo(dto.getReturnNo());
        update.setReturnTime(LocalDateTime.now());
        int affected = aftersaleMapper.update(update, Wrappers.<OmsAftersale>lambdaUpdate()
                .eq(OmsAftersale::getId, aftersale.getId())
                .eq(OmsAftersale::getStatus, AftersaleStatus.RETURNING.getCode()));
        if (affected == 0) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL);
        }
        log.info("买家填写退货物流 | aftersaleNo={} | {} {}", aftersale.getAftersaleNo(),
                dto.getReturnCompany(), dto.getReturnNo());
    }

    /* ==================== 管理端 ==================== */

    @Override
    public PageResult<AftersaleVO> adminPage(int pageNum, int pageSize, Integer status) {
        return pageAftersales(pageNum, pageSize, status, null);
    }

    @Override
    public AftersaleDetailVO adminDetail(Long id) {
        return buildDetail(requireAftersale(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, AftersaleAuditDTO dto) {
        OmsAftersale aftersale = requireAftersale(id);
        if (!Objects.equals(aftersale.getStatus(), AftersaleStatus.PENDING_AUDIT.getCode())) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL,
                    "只能在「待审核」阶段审核，当前为「" + statusDesc(aftersale.getStatus()) + "」");
        }
        Long adminId = SecurityUtils.getUserId();
        String adminName = SecurityUtils.getUsername();
        LocalDateTime now = LocalDateTime.now();

        // ---- 拒绝：关闭售后，订单回到售后前的状态 ----
        if (!Boolean.TRUE.equals(dto.getApprove())) {
            String remark = StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : "审核拒绝";
            transition(aftersale, AftersaleStatus.PENDING_AUDIT, AftersaleStatus.REJECTED,
                    adminId, now, remark, null, now);
            revertOrder(aftersale, ACTION_REJECT, "售后被拒绝：" + remark);
            log.info("售后审核拒绝 | aftersaleNo={} | 原因={}", aftersale.getAftersaleNo(), remark);
            return;
        }

        // ---- 同意：校验实际退款金额 ----
        AftersaleType type = AftersaleType.of(aftersale.getType());
        long actual = dto.getActualRefundAmount() == null
                ? nz(aftersale.getRefundAmount()) : dto.getActualRefundAmount();
        if (actual <= 0 || actual > nz(aftersale.getRefundAmount())) {
            throw new BizException(ResultCode.AFTERSALE_AMOUNT_INVALID,
                    "实际退款金额需在 1 ~ " + nz(aftersale.getRefundAmount()) + " 分之间，当前 " + actual);
        }

        if (type.needsReturn()) {
            // 退货退款：同意后进入「退货中」，等买家寄回。此时【不退款、不回补库存】
            transition(aftersale, AftersaleStatus.PENDING_AUDIT, AftersaleStatus.RETURNING,
                    adminId, now, dto.getRemark(), actual, null);
            log.info("售后审核同意（退货退款，等待买家寄回）| aftersaleNo={} | 金额={}",
                    aftersale.getAftersaleNo(), actual);
        } else {
            // 仅退款：同意后立即退款完成
            transition(aftersale, AftersaleStatus.PENDING_AUDIT, AftersaleStatus.APPROVED,
                    adminId, now, dto.getRemark(), actual, null);
            executeRefund(aftersale, actual, OPERATOR_ADMIN, adminId, adminName);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long id) {
        OmsAftersale aftersale = requireAftersale(id);
        if (!Objects.equals(aftersale.getStatus(), AftersaleStatus.RETURNING.getCode())) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL,
                    "当前状态为「" + statusDesc(aftersale.getStatus()) + "」，不能确认收货");
        }
        Long adminId = SecurityUtils.getUserId();
        String adminName = SecurityUtils.getUsername();
        LocalDateTime now = LocalDateTime.now();

        // 退货中 → 已收货（附收货时间），随后退款
        OmsAftersale update = new OmsAftersale();
        update.setStatus(AftersaleStatus.RECEIVED.getCode());
        update.setReceiveTime(now);
        int affected = aftersaleMapper.update(update, Wrappers.<OmsAftersale>lambdaUpdate()
                .eq(OmsAftersale::getId, aftersale.getId())
                .eq(OmsAftersale::getStatus, AftersaleStatus.RETURNING.getCode()));
        if (affected == 0) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL);
        }
        // 重新赋状态，便于 executeRefund 用正确的 from
        aftersale.setStatus(AftersaleStatus.RECEIVED.getCode());

        long actual = nz(aftersale.getActualRefundAmount()) > 0
                ? nz(aftersale.getActualRefundAmount()) : nz(aftersale.getRefundAmount());
        executeRefund(aftersale, actual, OPERATOR_ADMIN, adminId, adminName);
        log.info("商家确认收到退货并退款 | aftersaleNo={} | 金额={}", aftersale.getAftersaleNo(), actual);
    }

    @Override
    public List<Long> findTimeoutAftersaleIds(int limit) {
        // 只自动关闭「待审核」的：商家 7 天不处理说明不了了之。
        // 「退货中」的不自动关 —— 买家可能已经寄出，自动关掉会让货和钱都悬空
        return aftersaleMapper.selectList(Wrappers.<OmsAftersale>lambdaQuery()
                        .select(OmsAftersale::getId)
                        .eq(OmsAftersale::getStatus, AftersaleStatus.PENDING_AUDIT.getCode())
                        .lt(OmsAftersale::getAutoCloseTime, LocalDateTime.now())
                        .orderByAsc(OmsAftersale::getId)
                        .last("LIMIT " + limit))
                .stream().map(OmsAftersale::getId).toList();
    }

    /**
     * 关闭单笔超时售后（独立事务）。
     * <p>{@code @Override} 意味着它是接口方法，<b>由外部 Bean（定时任务）逐笔调用</b>，
     * 因此 Spring 代理会生效，{@code @Transactional} 才有意义。
     * 如果在同一类里循环调用自己，事务会静默失效 —— 那样一笔失败会回滚整批。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOne(Long aftersaleId) {
        OmsAftersale aftersale = aftersaleMapper.selectById(aftersaleId);
        if (aftersale == null) {
            return false;
        }
        if (!Objects.equals(aftersale.getStatus(), AftersaleStatus.PENDING_AUDIT.getCode())) {
            return false;
        }
        int affected = aftersaleMapper.update(statusOnly(AftersaleStatus.CLOSED, LocalDateTime.now()),
                Wrappers.<OmsAftersale>lambdaUpdate()
                        .eq(OmsAftersale::getId, aftersale.getId())
                        .eq(OmsAftersale::getStatus, AftersaleStatus.PENDING_AUDIT.getCode()));
        if (affected == 0) {
            return false;
        }
        aftersale.setStatus(AftersaleStatus.CLOSED.getCode());
        revertOrder(aftersale, ACTION_CANCEL, "超时未处理，系统自动关闭");
        log.info("超时自动关闭售后 | aftersaleNo={}", aftersale.getAftersaleNo());
        return true;
    }

    /* ==================== 核心：执行退款 ==================== */

    /**
     * 执行退款。<b>本模块最关键的一段。</b>
     * <p>顺序经过刻意安排：
     * <ol>
     *   <li>先找回原支付单 —— 退款是退到原来那笔支付上，没有它就退不了；</li>
     *   <li>写退款单（{@code pay_refund}），并<b>靠 {@code uk_aftersale_id} 唯一索引保证只退一次</b>；</li>
     *   <li>更新明细的退款状态与已退金额（用于「还能退多少」的计算）；</li>
     *   <li>更新支付单状态（部分/全额退款）；</li>
     *   <li><b>只有「退货退款」才回补库存</b> —— 仅退款时货还在买家手里；</li>
     *   <li>最后推进售后单与订单状态。</li>
     * </ol>
     */
    private void executeRefund(OmsAftersale aftersale, long amount,
                               int operatorType, Long operatorId, String operatorName) {
        // 1. 原支付单必须存在且成功
        PayPayment payment = paymentMapper.selectOne(Wrappers.<PayPayment>lambdaQuery()
                .eq(PayPayment::getOrderId, aftersale.getOrderId())
                .eq(PayPayment::getStatus, PaymentStatus.SUCCESS.getCode())
                .orderByDesc(PayPayment::getId)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BizException(ResultCode.AFTERSALE_NO_PAYMENT);
        }

        OmsOrderItem item = orderItemMapper.selectById(aftersale.getOrderItemId());
        if (item == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND, "订单明细不存在");
        }

        // 2. 退款单。refund_type：退满该明细实付则算「全额」，否则「部分」
        long itemPayAmount = nz(item.getPayAmount());
        PayRefund refund = new PayRefund();
        refund.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        refund.setRefundNo(generateRefundNo());
        refund.setPaymentNo(payment.getPaymentNo());
        refund.setOrderId(aftersale.getOrderId());
        refund.setOrderNo(aftersale.getOrderNo());
        refund.setAftersaleId(aftersale.getId());
        refund.setUserId(aftersale.getUserId());
        refund.setChannel(payment.getChannel());
        refund.setRefundAmount(amount);
        refund.setRefundType(amount >= itemPayAmount ? REFUND_TYPE_FULL : REFUND_TYPE_PARTIAL);
        // 模拟渠道：直接成功。真实渠道这里应为「退款中(1)」，等回调再置成功
        refund.setStatus(REFUND_STATUS_SUCCESS);
        refund.setChannelRefundNo("MOCKREFUND" + IdWorker.getId());
        refund.setReason(aftersale.getReason());
        refund.setRetryCount(0);
        refund.setRefundTime(LocalDateTime.now());
        refund.setVersion(0);
        // uk_aftersale_id 唯一索引在这里兜底：同一售后重复退款会直接撞键
        refundMapper.insert(refund);

        // 3. 明细的退款状态与已退金额
        long newRefunded = nz(item.getRefundAmount()) + amount;
        OmsOrderItem itemUpdate = new OmsOrderItem();
        itemUpdate.setId(item.getId());
        itemUpdate.setRefundAmount(newRefunded);
        itemUpdate.setRefundStatus(newRefunded >= itemPayAmount ? ITEM_REFUND_FULL : ITEM_REFUND_PARTIAL);
        orderItemMapper.updateById(itemUpdate);

        // 4. 支付单状态：整单退完才是「全额退款」，否则「部分退款」
        PayPayment paymentUpdate = new PayPayment();
        paymentUpdate.setId(payment.getId());
        boolean wholeOrderRefunded = refund.getRefundType() == REFUND_TYPE_FULL
                && isAllItemsFullyRefunded(aftersale.getOrderId(), item.getId());
        paymentUpdate.setStatus(wholeOrderRefunded
                ? PaymentStatus.REFUNDED.getCode() : PaymentStatus.PARTIAL_REFUNDED.getCode());
        paymentMapper.updateById(paymentUpdate);

        // 5. ⚠️ 只有【退货退款】才回补库存：货退回来了才重新可售。
        //    仅退款时货还在买家手里，回补库存就等于凭空多一件，最终导致超卖。
        AftersaleType type = AftersaleType.of(aftersale.getType());
        if (type.refundsStock()) {
            int refundQty = aftersale.getQuantity() == null ? 1 : aftersale.getQuantity();
            stockService.refundStock(item.getSkuId(), refundQty, aftersale.getOrderNo(), refund.getRefundNo());
        }

        // 6. 售后单 → 退款完成
        transition(aftersale, AftersaleStatus.of(aftersale.getStatus()), AftersaleStatus.REFUNDED,
                null, null, null, amount, LocalDateTime.now());

        // 7. 订单 → 已退款。
        //    ⚠️ 只有【整单所有明细都退完】才能把订单置为已退款。
        //    多商品订单里退掉一件就把整单标成「已退款」，剩下那件就再也退不了了
        //    （它的售后完成时会发现订单已不在「售后中」，状态推不动）。
        if (wholeOrderRefunded) {
            int orderAffected = orderMapper.updateStatus(aftersale.getOrderId(),
                    OrderStatus.AFTER_SALE.getCode(), OrderStatus.REFUNDED.getCode(),
                    null, null, null, null, null, null, null, null, null, null);
            if (orderAffected == 0) {
                throw new BizException(ResultCode.ORDER_STATUS_CHANGED, "订单状态已变更，退款未能完成推进");
            }
            OmsOrder order = orderMapper.selectById(aftersale.getOrderId());
            writeOrderHistory(order, ACTION_REFUND, OrderStatus.AFTER_SALE.getCode(),
                    OrderStatus.REFUNDED.getCode(), operatorType, operatorId, operatorName,
                    "售后退款完成，金额 " + amount + " 分，退款单 " + refund.getRefundNo());
        } else {
            // 还有其它明细没退完：订单保持「售后中」，只记一条流水便于排查
            OmsOrder order = orderMapper.selectById(aftersale.getOrderId());
            writeOrderHistory(order, ACTION_REFUND, OrderStatus.AFTER_SALE.getCode(),
                    OrderStatus.AFTER_SALE.getCode(), operatorType, operatorId, operatorName,
                    "部分退款完成（订单仍有其它商品在售后），金额 " + amount + " 分，退款单 " + refund.getRefundNo());
        }

        log.info("退款完成 | aftersaleNo={} | refundNo={} | 金额={} | 回补库存={}",
                aftersale.getAftersaleNo(), refund.getRefundNo(), amount, type.refundsStock());
    }

    /** 该订单除本次之外的所有明细是否都已全额退款（用于判断支付单是「全额」还是「部分」退款） */
    private boolean isAllItemsFullyRefunded(Long orderId, Long currentItemId) {
        List<OmsOrderItem> items = orderItemMapper.selectList(Wrappers.<OmsOrderItem>lambdaQuery()
                .eq(OmsOrderItem::getOrderId, orderId));
        for (OmsOrderItem it : items) {
            if (Objects.equals(it.getId(), currentItemId)) {
                // 当前明细刚被改成全额退款，视为已退完
                continue;
            }
            if (!Objects.equals(it.getRefundStatus(), ITEM_REFUND_FULL)) {
                return false;
            }
        }
        return true;
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 把订单从「售后中」还原到售后之前的状态。
     * <p><b>原状态是推导出来的</b>：{@code receive_time} 为空说明用户还没确认收货，
     * 那售后前就是「待收货」；非空则是「已完成」。
     * 这样不需要在订单表里再冗余一个 {@code pre_aftersale_status} 字段。
     */
    private void revertOrder(OmsAftersale aftersale, String action, String note) {
        OmsOrder order = orderMapper.selectById(aftersale.getOrderId());
        if (order == null) {
            return;
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.AFTER_SALE.getCode())) {
            // 已经被别的路径（如退款完成）推进走了，不再还原
            log.info("订单已不在「售后中」，跳过还原 | orderNo={} | status={}",
                    order.getOrderNo(), order.getStatus());
            return;
        }
        // ⚠️ 多商品订单的关键判断：只有当【这笔订单再也没有其它进行中的售后】时才还原订单状态。
        //    否则「退掉商品 A 的申请被拒」会把仍有一个商品在售后中的订单错误地拉回「待收货」，
        //    之后那笔售后完成时就再也推不动订单了（状态对不上）。
        Long otherActive = aftersaleMapper.selectCount(Wrappers.<OmsAftersale>lambdaQuery()
                .eq(OmsAftersale::getOrderId, order.getId())
                .ne(OmsAftersale::getId, aftersale.getId())
                .in(OmsAftersale::getStatus,
                        AftersaleStatus.PENDING_AUDIT.getCode(),
                        AftersaleStatus.APPROVED.getCode(),
                        AftersaleStatus.RETURNING.getCode(),
                        AftersaleStatus.RECEIVED.getCode()));
        if (otherActive != null && otherActive > 0) {
            log.info("订单还有其它进行中的售后，保持「售后中」不还原 | orderNo={} | 其它售后数={}",
                    order.getOrderNo(), otherActive);
            return;
        }
        OrderStatus target = order.getReceiveTime() == null
                ? OrderStatus.PENDING_RECEIPT : OrderStatus.COMPLETED;
        int affected = orderMapper.updateStatus(order.getId(), OrderStatus.AFTER_SALE.getCode(),
                target.getCode(), null, null, null, null, null, null, null, null, null, null);
        if (affected == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED);
        }
        writeOrderHistory(order, action, OrderStatus.AFTER_SALE.getCode(), target.getCode(),
                OPERATOR_SYSTEM, null, "系统", note);
    }

    /**
     * 售后单状态流转（条件更新）。
     * <p>受影响行数为 0 表示状态已被其它请求改过，必须抛异常 ——
     * 否则会出现「同一售后被退款两次」这类问题。
     */
    private void transition(OmsAftersale aftersale, AftersaleStatus from, AftersaleStatus to,
                            Long auditUserId, LocalDateTime auditTime, String auditRemark,
                            Long actualRefundAmount, LocalDateTime finishTime) {
        if (from.isFinal()) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL,
                    "售后已处于终态「" + from.getDesc() + "」，不能再变更");
        }
        OmsAftersale update = statusOnly(to, finishTime);
        update.setAuditUserId(auditUserId);
        update.setAuditTime(auditTime);
        update.setAuditRemark(auditRemark);
        update.setActualRefundAmount(actualRefundAmount);
        int affected = aftersaleMapper.update(update, Wrappers.<OmsAftersale>lambdaUpdate()
                .eq(OmsAftersale::getId, aftersale.getId())
                .eq(OmsAftersale::getStatus, from.getCode()));
        if (affected == 0) {
            throw new BizException(ResultCode.AFTERSALE_STATUS_ILLEGAL,
                    "售后状态已变更（期望「" + from.getDesc() + "」），请刷新后重试");
        }
        aftersale.setStatus(to.getCode());
        if (actualRefundAmount != null) {
            aftersale.setActualRefundAmount(actualRefundAmount);
        }
    }

    private static OmsAftersale statusOnly(AftersaleStatus to, LocalDateTime finishTime) {
        OmsAftersale update = new OmsAftersale();
        update.setStatus(to.getCode());
        update.setFinishTime(finishTime);
        return update;
    }

    private PageResult<AftersaleVO> pageAftersales(int pageNum, int pageSize, Integer status, Long userId) {
        var wrapper = Wrappers.<OmsAftersale>lambdaQuery()
                .eq(userId != null, OmsAftersale::getUserId, userId)
                .eq(status != null, OmsAftersale::getStatus, status)
                .orderByDesc(OmsAftersale::getId);
        var result = aftersaleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<OmsAftersale> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }
        // 批量取明细快照，避免逐行查询（N+1）
        List<Long> itemIds = records.stream().map(OmsAftersale::getOrderItemId).distinct().toList();
        Map<Long, OmsOrderItem> itemMap = orderItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(OmsOrderItem::getId, i -> i, (a, b) -> a));
        List<AftersaleVO> vos = records.stream()
                .map(a -> toVO(a, itemMap.get(a.getOrderItemId())))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), vos);
    }

    private AftersaleDetailVO buildDetail(OmsAftersale aftersale) {
        OmsOrderItem item = orderItemMapper.selectById(aftersale.getOrderItemId());
        AftersaleVO base = toVO(aftersale, item);

        AftersaleDetailVO vo = new AftersaleDetailVO();
        // 复制列表字段
        vo.setId(base.getId());
        vo.setAftersaleNo(base.getAftersaleNo());
        vo.setOrderId(base.getOrderId());
        vo.setOrderNo(base.getOrderNo());
        vo.setOrderItemId(base.getOrderItemId());
        vo.setType(base.getType());
        vo.setTypeDesc(base.getTypeDesc());
        vo.setStatus(base.getStatus());
        vo.setStatusDesc(base.getStatusDesc());
        vo.setReason(base.getReason());
        vo.setRefundAmount(base.getRefundAmount());
        vo.setActualRefundAmount(base.getActualRefundAmount());
        vo.setQuantity(base.getQuantity());
        vo.setItemName(base.getItemName());
        vo.setItemImage(base.getItemImage());
        vo.setSpecDesc(base.getSpecDesc());
        vo.setItemPayAmount(base.getItemPayAmount());
        vo.setCreateTime(base.getCreateTime());
        vo.setFinishTime(base.getFinishTime());

        vo.setDescription(aftersale.getDescription());
        vo.setImages(fromJson(aftersale.getImages()));
        vo.setAuditRemark(aftersale.getAuditRemark());
        vo.setAuditTime(aftersale.getAuditTime());
        vo.setReturnCompany(aftersale.getReturnCompany());
        vo.setReturnNo(aftersale.getReturnNo());
        vo.setReturnTime(aftersale.getReturnTime());
        vo.setReceiveTime(aftersale.getReceiveTime());
        vo.setAutoCloseTime(aftersale.getAutoCloseTime());

        PayRefund refund = refundMapper.selectOne(Wrappers.<PayRefund>lambdaQuery()
                .eq(PayRefund::getAftersaleId, aftersale.getId())
                .last("LIMIT 1"));
        if (refund != null) {
            vo.setRefundNo(refund.getRefundNo());
        }
        return vo;
    }

    private static AftersaleVO toVO(OmsAftersale a, OmsOrderItem item) {
        AftersaleVO vo = new AftersaleVO();
        vo.setId(a.getId());
        vo.setAftersaleNo(a.getAftersaleNo());
        vo.setOrderId(a.getOrderId());
        vo.setOrderNo(a.getOrderNo());
        vo.setOrderItemId(a.getOrderItemId());
        vo.setType(a.getType());
        vo.setStatus(a.getStatus());
        vo.setReason(a.getReason());
        vo.setRefundAmount(a.getRefundAmount());
        vo.setActualRefundAmount(a.getActualRefundAmount());
        vo.setQuantity(a.getQuantity());
        vo.setCreateTime(a.getCreateTime());
        vo.setFinishTime(a.getFinishTime());
        try {
            vo.setTypeDesc(AftersaleType.of(a.getType()).getDesc());
        } catch (IllegalArgumentException e) {
            vo.setTypeDesc("未知");
        }
        vo.setStatusDesc(statusDesc(a.getStatus()));
        if (item != null) {
            // 商品信息读的是【订单明细快照】，不是商品表 —— 商品改名改价不能影响售后单
            vo.setItemName(item.getSkuName());
            vo.setItemImage(item.getSkuImage());
            vo.setSpecDesc(item.getSpecDesc());
            vo.setItemPayAmount(item.getPayAmount());
        }
        return vo;
    }

    private static String statusDesc(int code) {
        try {
            return AftersaleStatus.of(code).getDesc();
        } catch (IllegalArgumentException e) {
            return "未知(" + code + ")";
        }
    }

    private OmsAftersale requireOwnedAftersale(Long id) {
        Long userId = SecurityUtils.getAppUserId();
        OmsAftersale aftersale = aftersaleMapper.selectOne(Wrappers.<OmsAftersale>lambdaQuery()
                .eq(OmsAftersale::getId, id)
                .eq(OmsAftersale::getUserId, userId));
        if (aftersale == null) {
            // 返回「不存在」而不是 403：避免探测他人售后单
            throw new BizException(ResultCode.AFTERSALE_NOT_FOUND);
        }
        return aftersale;
    }

    private OmsAftersale requireAftersale(Long id) {
        OmsAftersale aftersale = aftersaleMapper.selectById(id);
        if (aftersale == null) {
            throw new BizException(ResultCode.AFTERSALE_NOT_FOUND);
        }
        return aftersale;
    }

    private static AftersaleType resolveType(Integer type) {
        if (type == null) {
            throw new BizException(ResultCode.AFTERSALE_TYPE_UNSUPPORTED, "未指定售后类型");
        }
        AftersaleType t;
        try {
            t = AftersaleType.of(type);
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.AFTERSALE_TYPE_UNSUPPORTED, "类型值：" + type);
        }
        if (t == AftersaleType.EXCHANGE) {
            throw new BizException(ResultCode.AFTERSALE_TYPE_UNSUPPORTED, "换货流程暂未开放");
        }
        return t;
    }

    private void writeOrderHistory(OmsOrder order, String action, int fromStatus, int toStatus,
                                   int operatorType, Long operatorId, String operatorName, String note) {
        if (order == null) {
            return;
        }
        OmsOrderOperateHistory history = new OmsOrderOperateHistory();
        history.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        history.setOrderId(order.getId());
        history.setOrderNo(order.getOrderNo());
        history.setAction(action);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorType(operatorType);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setNote(note);
        history.setTraceId(MDC.get("traceId"));
        historyMapper.insert(history);
    }

    private String generateAftersaleNo() {
        return "AS" + LocalDateTime.now().format(NO_DATE) + IdWorker.getId();
    }

    private String generateRefundNo() {
        return "RF" + LocalDateTime.now().format(NO_DATE) + IdWorker.getId();
    }

    private String toJson(List<String> images) {
        if (CollectionUtils.isEmpty(images)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            log.warn("售后凭证图片序列化失败，按空处理", e);
            return null;
        }
    }

    private List<String> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            log.warn("售后凭证图片反序列化失败 | json={}", json, e);
            return new ArrayList<>();
        }
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }
}
