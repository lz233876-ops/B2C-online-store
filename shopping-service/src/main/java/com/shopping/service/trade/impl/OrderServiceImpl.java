package com.shopping.service.trade.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shopping.common.constant.CommonConstants;
import com.shopping.common.exception.BizException;
import com.shopping.common.result.PageResult;
import com.shopping.common.result.ResultCode;
import com.shopping.common.util.MaskUtils;
import com.shopping.dao.mapper.OmsCartItemMapper;
import com.shopping.dao.mapper.OmsOrderItemMapper;
import com.shopping.dao.mapper.OmsOrderMapper;
import com.shopping.dao.mapper.OmsOrderOperateHistoryMapper;
import com.shopping.dao.mapper.PmsSkuMapper;
import com.shopping.dao.mapper.PmsSpuMapper;
import com.shopping.dao.mapper.UmsUserAddressMapper;
import com.shopping.model.dto.OrderSubmitDTO;
import com.shopping.model.entity.OmsCartItem;
import com.shopping.model.entity.OmsOrder;
import com.shopping.model.entity.OmsOrderItem;
import com.shopping.model.entity.OmsOrderOperateHistory;
import com.shopping.model.entity.PmsSku;
import com.shopping.model.entity.PmsSpu;
import com.shopping.model.entity.UmsUserAddress;
import com.shopping.model.enums.OrderStatus;
import com.shopping.model.vo.OrderDetailVO;
import com.shopping.model.vo.OrderItemVO;
import com.shopping.model.vo.OrderSubmitVO;
import com.shopping.model.vo.OrderVO;
import com.shopping.service.auth.security.SecurityUtils;
import com.shopping.service.cart.CartService;
import com.shopping.service.inventory.StockService;
import com.shopping.service.trade.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
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
public class OrderServiceImpl implements OrderService {

    /** 免运费门槛：99 元（分） */
    private static final long FREE_FREIGHT_THRESHOLD = 9900L;
    /** 运费：10 元（分） */
    private static final long FREIGHT_FEE = 1000L;

    /** 操作人类型 */
    private static final int OPERATOR_USER = 1;
    private static final int OPERATOR_ADMIN = 2;
    private static final int OPERATOR_SYSTEM = 3;

    /** 动作 */
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_CANCEL = "CANCEL";
    private static final String ACTION_TIMEOUT_CLOSE = "TIMEOUT_CLOSE";
    private static final String ACTION_DELIVER = "DELIVER";
    private static final String ACTION_RECEIVE = "RECEIVE";

    private static final int SPU_ON_SALE = 1;
    private static final int SPU_PUBLISHED = 2;
    private static final int SELECTED = 1;

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OmsOrderMapper orderMapper;
    private final OmsOrderItemMapper orderItemMapper;
    private final OmsOrderOperateHistoryMapper historyMapper;
    private final OmsCartItemMapper cartItemMapper;
    private final UmsUserAddressMapper addressMapper;
    private final PmsSkuMapper skuMapper;
    private final PmsSpuMapper spuMapper;
    private final StockService stockService;
    private final CartService cartService;
    private final com.shopping.service.pay.PaymentService paymentService;

    /** 自动关闭分钟数，可配置：大促时运营可以调长，测试时可以调短 */
    @Value("${shopping.order.auto-close-minutes:30}")
    private int autoCloseMinutes;

    /* ==================== 下单 ==================== */

    /**
     * 下单主链路。
     * <p><b>整个方法是一个事务</b>，顺序经过精心安排：
     * <ol>
     *   <li>先把能失败的前置校验全部做完（地址、商品在售、库存）——
     *       尽早失败可以少做无用的写操作，也少占一会儿行锁；</li>
     *   <li>再写订单（拿到 orderNo，它是库存流水的 biz_no）；</li>
     *   <li>再锁库存、写明细、写流水、清购物车。</li>
     * </ol>
     * 任何一步抛异常，前面所有写入（含已锁定的库存）都会回滚。
     * <p>
     * ⚠️ <b>锁库存必须在事务里、和建订单原子完成。</b>如果先建订单再异步锁库存，
     * 会出现「订单已存在但库存没锁上」——用户付了钱却发不出货。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submit(OrderSubmitDTO dto) {
        Long userId = SecurityUtils.getAppUserId();

        // 1. 地址必须属于当前会员（user_id 直接进 WHERE，防水平越权）
        UmsUserAddress address = addressMapper.selectOne(Wrappers.<UmsUserAddress>lambdaQuery()
                .eq(UmsUserAddress::getId, dto.getAddressId())
                .eq(UmsUserAddress::getUserId, userId));
        if (address == null) {
            throw new BizException(ResultCode.ADDRESS_NOT_FOUND);
        }

        // 2. 取购物车中【已勾选】的项
        List<OmsCartItem> cartItems = cartItemMapper.selectList(Wrappers.<OmsCartItem>lambdaQuery()
                .eq(OmsCartItem::getUserId, userId)
                .eq(OmsCartItem::getSelected, SELECTED)
                .orderByAsc(OmsCartItem::getId));
        if (CollectionUtils.isEmpty(cartItems)) {
            throw new BizException(ResultCode.CART_EMPTY);
        }

        // 3. 逐个校验商品仍在售，并组装订单明细（价格一律取【实时价】，不信任购物车快照）
        List<OmsOrderItem> items = new ArrayList<>(cartItems.size());
        long totalAmount = 0L;
        for (OmsCartItem cartItem : cartItems) {
            PmsSku sku = skuMapper.selectById(cartItem.getSkuId());
            if (sku == null || !Objects.equals(sku.getStatus(), 1)) {
                throw new BizException(ResultCode.SKU_NOT_FOUND,
                        "「" + cartItem.getSkuName() + "」已下架，请从购物车移除后再结算");
            }
            PmsSpu spu = spuMapper.selectById(sku.getSpuId());
            if (spu == null || !Objects.equals(spu.getStatus(), SPU_ON_SALE)
                    || !Objects.equals(spu.getPublishStatus(), SPU_PUBLISHED)) {
                throw new BizException(ResultCode.PRODUCT_NOT_FOUND,
                        "「" + cartItem.getSkuName() + "」已下架，请从购物车移除后再结算");
            }

            // 结算单价用 SKU 的实时价。⚠️ 绝不能信购物车里的 price 快照：
            // 它是展示用的，如果拿它结算，用户把商品放购物车很久之后就能用旧价买到
            long price = sku.getPrice() == null ? 0L : sku.getPrice();
            long itemTotal = price * cartItem.getQuantity();
            totalAmount += itemTotal;

            OmsOrderItem item = new OmsOrderItem();
            item.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
            item.setUserId(userId);
            item.setSpuId(sku.getSpuId());
            item.setSkuId(sku.getId());
            // 快照：下单这一刻的名称/图片/规格，商品之后怎么改都不影响这一单
            item.setSpuName(spu.getSpuName());
            item.setSkuName(sku.getSkuName());
            item.setSkuImage(sku.getImage());
            item.setSpecDesc(sku.getSpecDesc());
            item.setSkuCode(sku.getSkuCode());
            item.setPrice(price);
            item.setQuantity(cartItem.getQuantity());
            item.setTotalAmount(itemTotal);
            item.setDiscountAmount(0L);
            item.setPayAmount(itemTotal);
            item.setRefundStatus(0);
            item.setRefundAmount(0L);
            item.setIsCommented(0);
            items.add(item);
        }

        // 4. 算钱。运费规则先做最简单的「满 99 免运费」，Phase 3 再接入优惠券
        long freightAmount = totalAmount >= FREE_FREIGHT_THRESHOLD ? 0L : FREIGHT_FEE;
        long payAmount = totalAmount + freightAmount;
        // 应付金额不允许为负：优惠叠加出错时必须兜底，否则会出现「平台倒欠用户钱」
        if (payAmount < 0) {
            payAmount = 0L;
        }

        // 5. 建订单
        String orderNo = generateOrderNo();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime autoCloseTime = now.plusMinutes(autoCloseMinutes);

        OmsOrder order = new OmsOrder();
        order.setMerchantId(CommonConstants.DEFAULT_MERCHANT_ID);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setOrderType(1);
        order.setSourceType(dto.getSourceType() == null ? 2 : dto.getSourceType());
        order.setTotalAmount(totalAmount);
        order.setFreightAmount(freightAmount);
        order.setDiscountAmount(0L);
        order.setCouponAmount(0L);
        order.setPointsAmount(0L);
        order.setMemberDiscountAmount(0L);
        order.setPayAmount(payAmount);
        order.setPaidAmount(0L);
        order.setUsePoints(0);
        order.setGainPoints(0);
        order.setGainGrowth(0);
        order.setPayType(0);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        // 收货人信息落【快照】：用户之后改地址或删地址，都不能影响已经下过的单
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverProvince(address.getProvince());
        order.setReceiverCity(address.getCity());
        order.setReceiverDistrict(address.getDistrict());
        order.setReceiverDetailAddress(address.getDetailAddress());
        order.setNote(dto.getNote());
        order.setVersion(0);
        // 超时时间落库而不是实时算：扫描时可以走索引，也允许运营单独调整
        order.setAutoCloseTime(autoCloseTime);
        orderMapper.insert(order);

        // 6. 锁库存 + 写明细。锁库存失败会抛异常 → 整个事务回滚（订单也不会留下）
        List<Long> skuIds = new ArrayList<>(items.size());
        for (OmsOrderItem item : items) {
            stockService.lockStock(item.getSkuId(), item.getQuantity(), orderNo);
            item.setOrderId(order.getId());
            item.setOrderNo(orderNo);
            orderItemMapper.insert(item);
            skuIds.add(item.getSkuId());
        }

        // 7. 写状态流水（创建订单：fromStatus = -1 表示无前置状态）
        writeHistory(order, ACTION_CREATE, -1, OrderStatus.PENDING_PAYMENT.getCode(),
                OPERATOR_USER, userId, SecurityUtils.getAppLoginUser().getNickname(), "提交订单");

        // 8. 清购物车：只清已下单的那些 SKU（未勾选的保留）
        cartService.clearBySkuIds(userId, skuIds);

        log.info("下单成功 | orderNo={} | userId={} | 商品总额={} | 运费={} | 应付={} | 自动关闭={}",
                orderNo, userId, totalAmount, freightAmount, payAmount, autoCloseTime);

        OrderSubmitVO vo = new OrderSubmitVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(orderNo);
        vo.setPayAmount(payAmount);
        vo.setAutoCloseTime(autoCloseTime);
        return vo;
    }

    /**
     * 生成订单号。
     * <p>形如 {@code SO20261109 + 雪花ID}。<b>用雪花 ID 而不是随机数</b>：
     * 订单号有唯一索引，随机数在并发下要靠「撞了再重试」来保证唯一，而雪花 ID 本身全局唯一，
     * 一次生成即可，不需要重试逻辑。日期前缀是为了运维按天排查时肉眼可分。
     */
    private String generateOrderNo() {
        return "SO" + LocalDateTime.now().format(ORDER_NO_DATE) + IdWorker.getId();
    }

    /* ==================== 查询 ==================== */

    @Override
    public PageResult<OrderVO> pageOfCurrentUser(int pageNum, int pageSize, Integer status) {
        return pageOrders(pageNum, pageSize, status, null, SecurityUtils.getAppUserId());
    }

    @Override
    public PageResult<OrderVO> adminPage(int pageNum, int pageSize, Integer status, String orderNo) {
        // userId = null 表示不按会员过滤
        return pageOrders(pageNum, pageSize, status, orderNo, null);
    }

    private PageResult<OrderVO> pageOrders(int pageNum, int pageSize, Integer status,
                                           String orderNo, Long userId) {
        var wrapper = Wrappers.<OmsOrder>lambdaQuery()
                .eq(userId != null, OmsOrder::getUserId, userId)
                .eq(status != null, OmsOrder::getStatus, status)
                .eq(StringUtils.hasText(orderNo), OmsOrder::getOrderNo, orderNo)
                .orderByDesc(OmsOrder::getId);

        IPage<OmsOrder> result = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<OmsOrder> orders = result.getRecords();
        if (orders.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 批量取明细件数：一次 IN 查询后分组，避免逐单查询（N+1）
        List<Long> orderIds = orders.stream().map(OmsOrder::getId).toList();
        Map<Long, Integer> quantityMap = new HashMap<>();
        for (OmsOrderItem item : orderItemMapper.selectList(Wrappers.<OmsOrderItem>lambdaQuery()
                .in(OmsOrderItem::getOrderId, orderIds))) {
            quantityMap.merge(item.getOrderId(), item.getQuantity(), Integer::sum);
        }

        List<OrderVO> vos = orders.stream()
                .map(o -> toVO(o, quantityMap.getOrDefault(o.getId(), 0)))
                .toList();
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), vos);
    }

    @Override
    public OrderDetailVO detailOfCurrentUser(Long orderId) {
        return buildDetail(requireOwnedOrder(orderId));
    }

    @Override
    public OrderDetailVO adminDetail(Long orderId) {
        OmsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return buildDetail(order);
    }

    private OrderDetailVO buildDetail(OmsOrder order) {
        List<OmsOrderItem> items = orderItemMapper.selectList(Wrappers.<OmsOrderItem>lambdaQuery()
                .eq(OmsOrderItem::getOrderId, order.getId())
                .orderByAsc(OmsOrderItem::getId));

        int totalQuantity = items.stream().mapToInt(OmsOrderItem::getQuantity).sum();
        OrderDetailVO vo = new OrderDetailVO();
        fill(vo, order, totalQuantity);

        vo.setItems(items.stream().map(OrderServiceImpl::toItemVO).toList());

        // 状态流水倒序：最新的在最前，前端直接顺序渲染时间轴
        vo.setHistories(historyMapper.selectList(Wrappers.<OmsOrderOperateHistory>lambdaQuery()
                        .eq(OmsOrderOperateHistory::getOrderId, order.getId())
                        .orderByDesc(OmsOrderOperateHistory::getId))
                .stream().map(h -> {
                    OrderDetailVO.OrderHistoryVO hv = new OrderDetailVO.OrderHistoryVO();
                    hv.setAction(h.getAction());
                    hv.setFromStatus(h.getFromStatus());
                    hv.setToStatus(h.getToStatus());
                    hv.setOperatorType(h.getOperatorType());
                    hv.setOperatorName(h.getOperatorName());
                    hv.setNote(h.getNote());
                    hv.setCreateTime(h.getCreateTime());
                    return hv;
                }).toList());
        return vo;
    }

    /* ==================== 状态流转 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, String reason) {
        OmsOrder order = requireOwnedOrder(orderId);
        // 用户只能取消【待付款】的订单。已付款的要走退款/售后流程，
        // 否则会出现「钱收了、货没发、订单变成已取消」的账实不符
        if (!Objects.equals(order.getStatus(), OrderStatus.PENDING_PAYMENT.getCode())) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "当前状态（" + currentStatusDesc(order) + "）不能取消，请申请售后");
        }

        String cancelReason = StringUtils.hasText(reason) ? reason : "用户主动取消";
        transition(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED,
                ACTION_CANCEL, OPERATOR_USER, order.getUserId(),
                SecurityUtils.getAppLoginUser().getNickname(), cancelReason,
                null, null, LocalDateTime.now(), cancelReason, null, null);

        // 释放库存：把冻结还回可售。
        // ⚠️ 必须在状态流转【成功之后】才释放 —— 如果先释放再流转，
        //    流转失败（比如并发下被别人取消）就会导致库存被释放两次。
        releaseStockOf(order);
        // 同时关闭该订单下「待支付」的支付单：订单都没了，收银台不该还能付款。
        // 顺序同样放在流转成功之后（否则订单取消失败却关了支付单，用户就付不了钱了）
        paymentService.closePendingPayments(order.getId(), "订单已取消：" + cancelReason);
        log.info("订单已取消 | orderNo={} | 原因={}", order.getOrderNo(), cancelReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long orderId) {
        OmsOrder order = requireOwnedOrder(orderId);
        transition(order, OrderStatus.PENDING_RECEIPT, OrderStatus.COMPLETED,
                ACTION_RECEIVE, OPERATOR_USER, order.getUserId(),
                SecurityUtils.getAppLoginUser().getNickname(), "确认收货",
                null, LocalDateTime.now(), null, null, null, null);
        log.info("订单已完成 | orderNo={}", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliver(Long orderId, String deliveryCompany, String deliveryNo) {
        OmsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!StringUtils.hasText(deliveryNo)) {
            throw new BizException(ResultCode.BAD_REQUEST, "物流单号不能为空");
        }
        transition(order, OrderStatus.PENDING_DELIVERY, OrderStatus.PENDING_RECEIPT,
                ACTION_DELIVER, OPERATOR_ADMIN, SecurityUtils.getUserId(),
                SecurityUtils.getUsername(), "订单发货",
                LocalDateTime.now(), null, null, null, deliveryCompany, deliveryNo);
        log.info("订单已发货 | orderNo={} | {} {}", order.getOrderNo(), deliveryCompany, deliveryNo);
    }

    /* ==================== 超时关单 ==================== */

    @Override
    public List<Long> findTimeoutOrderIds(int limit) {
        // ⚠️ 只扫「待付款 + 已过自动关闭时间」的订单。
        //    auto_close_time 是真实列（不是 create_time 的函数运算），所以能走索引；
        //    如果写成 create_time + INTERVAL 30 MINUTE < NOW()，索引会失效，大表上就是全表扫描。
        return orderMapper.selectList(Wrappers.<OmsOrder>lambdaQuery()
                        .select(OmsOrder::getId)
                        .eq(OmsOrder::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                        .lt(OmsOrder::getAutoCloseTime, LocalDateTime.now())
                        .orderByAsc(OmsOrder::getId)
                        .last("LIMIT " + limit))
                .stream().map(OmsOrder::getId).toList();
    }

    /**
     * 关闭单笔超时订单。
     * <p><b>幂等性来自条件更新</b>：如果用户恰好在扫描的同时完成了支付，
     * 这个 {@code WHERE status = 0} 会失败（受影响 0 行），于是本方法返回 false 并跳过，
     * <b>绝不会把一笔已支付的订单关掉</b>。
     * <p>这也是「为什么状态流转必须用条件更新」最有说服力的场景：
     * 支付回调和超时关单是两个独立的异步事件，它们<b>一定会</b>在某个边界上撞车。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeTimeoutOrder(Long orderId) {
        OmsOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.PENDING_PAYMENT.getCode())) {
            // 已被支付或被用户取消 —— 什么都不做，这是正常的竞争结果，不是错误
            return false;
        }

        int affected = orderMapper.updateStatus(orderId,
                OrderStatus.PENDING_PAYMENT.getCode(), OrderStatus.CANCELLED.getCode(),
                null, null, null, null, LocalDateTime.now(), "超时未支付，系统自动关闭",
                null, null, null, null);
        if (affected == 0) {
            log.info("超时关单跳过：状态已被其它请求修改 | orderNo={}", order.getOrderNo());
            return false;
        }

        writeHistory(order, ACTION_TIMEOUT_CLOSE,
                OrderStatus.PENDING_PAYMENT.getCode(), OrderStatus.CANCELLED.getCode(),
                OPERATOR_SYSTEM, null, "系统", "超时未支付");

        // 状态确实由我们改成功了，才释放库存（顺序不能反）
        releaseStockOf(order);
        paymentService.closePendingPayments(order.getId(), "订单超时未支付，系统自动关闭");
        log.info("超时关单成功 | orderNo={} | userId={}", order.getOrderNo(), order.getUserId());
        return true;
    }

    /* ==================== 内部方法 ==================== */

    /**
     * 统一的状态流转入口：<b>先查枚举规则，再做条件更新</b>。
     * <p>两层保护的分工：
     * <ul>
     *   <li>枚举 {@code canTransitionTo} 挡住「业务上就不该发生」的流转
     *       （比如已取消 → 待发货），能给出清晰的错误提示；</li>
     *   <li>条件更新挡住「并发下同时到达」的请求，这是数据库级别的保证。</li>
     * </ul>
     * 少了任何一层都不行：只有枚举 → 并发下两个请求都能通过检查；
     * 只有条件更新 → 「非法流转」要靠试错才发现，错误信息也没法解释原因。
     * <p>
     * 时间字段按<b>语义</b>分开传（{@code deliveryTime} / {@code receiveTime} / {@code closeTime}），
     * 不要用一个通用的「时间」参数——那样很容易把发货时间写成完成时间，
     * 而这种错误在测试里不跑物流全链路就发现不了。
     */
    private void transition(OmsOrder order, OrderStatus from, OrderStatus to,
                            String action, int operatorType, Long operatorId, String operatorName,
                            String note, LocalDateTime deliveryTime, LocalDateTime receiveTime,
                            LocalDateTime closeTime, String cancelReason,
                            String deliveryCompany, String deliveryNo) {
        if (!from.canTransitionTo(to)) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "订单状态不允许从「" + from.getDesc() + "」变更为「" + to.getDesc() + "」");
        }
        if (!Objects.equals(order.getStatus(), from.getCode())) {
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED,
                    "订单当前状态是「" + currentStatusDesc(order) + "」，请刷新后重试");
        }

        // 完成时间跟随收货时间（确认收货即完成）
        LocalDateTime finishTime = to == OrderStatus.COMPLETED
                ? (receiveTime == null ? LocalDateTime.now() : receiveTime)
                : null;

        int affected = orderMapper.updateStatus(order.getId(), from.getCode(), to.getCode(),
                null, deliveryTime, receiveTime, finishTime, closeTime, cancelReason,
                null, null, deliveryCompany, deliveryNo);
        if (affected == 0) {
            // 走到这里说明：读出来之后、更新之前，状态被另一个请求改了。
            // 必须抛异常让事务回滚，绝不能当作成功继续（那就会连累库存被错误处理）
            log.warn("订单状态流转失败（并发冲突）| orderNo={} | 期望={} | 目标={}",
                    order.getOrderNo(), from.getDesc(), to.getDesc());
            throw new BizException(ResultCode.ORDER_STATUS_CHANGED,
                    "订单状态已变更（" + from.getDesc() + " → " + to.getDesc() + " 失败），请刷新后重试");
        }
        writeHistory(order, action, from.getCode(), to.getCode(), operatorType, operatorId, operatorName, note);
    }

    private void writeHistory(OmsOrder order, String action, int fromStatus, int toStatus,
                              int operatorType, Long operatorId, String operatorName, String note) {
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

    /** 释放某订单占用的全部库存（逐项释放，每项都有流水） */
    private void releaseStockOf(OmsOrder order) {
        List<OmsOrderItem> items = orderItemMapper.selectList(Wrappers.<OmsOrderItem>lambdaQuery()
                .eq(OmsOrderItem::getOrderId, order.getId()));
        for (OmsOrderItem item : items) {
            stockService.releaseStock(item.getSkuId(), item.getQuantity(), order.getOrderNo());
        }
    }

    /** 查订单并校验归属：user_id 直接进 WHERE，而不是「查出来再比对」 */
    private OmsOrder requireOwnedOrder(Long orderId) {
        Long userId = SecurityUtils.getAppUserId();
        OmsOrder order = orderMapper.selectOne(Wrappers.<OmsOrder>lambdaQuery()
                .eq(OmsOrder::getId, orderId)
                .eq(OmsOrder::getUserId, userId));
        if (order == null) {
            // 返回「订单不存在」而不是 403：避免攻击者通过错误码探测订单是否存在
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private static String currentStatusDesc(OmsOrder order) {
        try {
            return OrderStatus.of(order.getStatus()).getDesc();
        } catch (IllegalArgumentException e) {
            return "未知(" + order.getStatus() + ")";
        }
    }

    private static OrderVO toVO(OmsOrder order, int totalQuantity) {
        OrderVO vo = new OrderVO();
        fill(vo, order, totalQuantity);
        return vo;
    }

    private static void fill(OrderVO vo, OmsOrder order, int totalQuantity) {
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setOrderType(order.getOrderType());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setFreightAmount(order.getFreightAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setPaidAmount(order.getPaidAmount());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setAutoCloseTime(order.getAutoCloseTime());
        vo.setDeliveryCompany(order.getDeliveryCompany());
        vo.setDeliveryNo(order.getDeliveryNo());
        vo.setCancelReason(order.getCancelReason());
        vo.setNote(order.getNote());
        vo.setTotalQuantity(totalQuantity);

        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(MaskUtils.phone(order.getReceiverPhone()));
        vo.setReceiverFullAddress(nullSafe(order.getReceiverProvince())
                + nullSafe(order.getReceiverCity())
                + nullSafe(order.getReceiverDistrict())
                + nullSafe(order.getReceiverDetailAddress()));

        // 优惠合计：把 4 个抵扣字段合成一个展示值（前端不必关心拆分）
        long discount = nz(order.getDiscountAmount()) + nz(order.getCouponAmount())
                + nz(order.getPointsAmount()) + nz(order.getMemberDiscountAmount());
        vo.setDiscountTotal(discount);

        try {
            OrderStatus status = OrderStatus.of(order.getStatus());
            vo.setStatusDesc(status.getDesc());
            // 剩余支付秒数交给前端做倒计时；已过期的返回 0（由定时任务负责真正关单）
            if (status == OrderStatus.PENDING_PAYMENT && order.getAutoCloseTime() != null) {
                long remain = Duration.between(LocalDateTime.now(), order.getAutoCloseTime()).getSeconds();
                vo.setRemainPaySeconds(Math.max(remain, 0L));
            } else {
                vo.setRemainPaySeconds(0L);
            }
        } catch (IllegalArgumentException e) {
            vo.setStatusDesc("未知");
            vo.setRemainPaySeconds(0L);
        }
    }

    private static OrderItemVO toItemVO(OmsOrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setId(item.getId());
        vo.setSpuId(item.getSpuId());
        vo.setSkuId(item.getSkuId());
        vo.setSpuName(item.getSpuName());
        vo.setSkuName(item.getSkuName());
        vo.setSkuImage(item.getSkuImage());
        vo.setSpecDesc(item.getSpecDesc());
        vo.setSkuCode(item.getSkuCode());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setTotalAmount(item.getTotalAmount());
        vo.setPayAmount(item.getPayAmount());
        vo.setRefundStatus(item.getRefundStatus());
        return vo;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
