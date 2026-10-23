package com.shopping.service.aftersale;

import com.shopping.common.result.PageResult;
import com.shopping.model.dto.AftersaleApplyDTO;
import com.shopping.model.dto.AftersaleAuditDTO;
import com.shopping.model.dto.AftersaleReturnDTO;
import com.shopping.model.vo.AftersaleDetailVO;
import com.shopping.model.vo.AftersaleVO;

/**
 * 售后服务。
 * <p>
 * <b>本模块最重要的三条业务规则（也是最容易写错的）：</b>
 * <ol>
 *   <li><b>售后挂在订单明细上</b>，不是整笔订单 —— 一笔订单买 3 件，可以只退 1 件；</li>
 *   <li><b>「仅退款」不回补库存，只有「退货退款」才回补</b> ——
 *       钱退了但货没回来，再加库存就凭空多一件货，账面虚高最终导致超卖；</li>
 *   <li><b>退款金额的上限是「该明细实付金额 - 已退金额」</b>，
 *       不是订单总额，也不是商品单价 × 数量（可能用过优惠券）。</li>
 * </ol>
 * <p>
 * <b>退款为什么是同步立即成功：</b>本项目是模拟渠道，退款直接置为成功。
 * 真实渠道的退款是<b>异步</b>的（发起 → 渠道处理 → 退款结果回调），
 * 因此 {@code pay_refund} 里预留了 {@code retry_count} / {@code next_retry_time} / {@code status=1退款中}。
 * <b>面试时要主动说明这一点，不要声称做完了完整的异步退款。</b>
 */
public interface AftersaleService {

    /** 用户申请售后（订单须为「待收货」或「已完成」） */
    Long apply(AftersaleApplyDTO dto);

    PageResult<AftersaleVO> pageOfCurrentUser(int pageNum, int pageSize, Integer status);

    AftersaleDetailVO detailOfCurrentUser(Long id);

    /** 用户在「待审核」阶段撤销申请（订单回到售后前的状态） */
    void cancelByUser(Long id);

    /** 买家填写退货物流（仅「退货中」状态可用） */
    void submitReturn(Long id, AftersaleReturnDTO dto);

    /* ---------------- 管理端 ---------------- */

    PageResult<AftersaleVO> adminPage(int pageNum, int pageSize, Integer status);

    AftersaleDetailVO adminDetail(Long id);

    /**
     * 审核。
     * <p>同意 + 仅退款 → 立即退款完成；
     * <p>同意 + 退货退款 → 进入「退货中」，等买家寄回；
     * <p>拒绝 → 售后关闭，<b>订单回到售后前的状态</b>。
     */
    void audit(Long id, AftersaleAuditDTO dto);

    /** 商家确认收到退货 → 触发退款 + 回补库存 */
    void confirmReceive(Long id);

    /**
     * 扫描出「待审核且已超时」的售后单 ID（只查询，不改状态）。
     * <p>与 {@link #closeOne(Long)} 分开是刻意的：批量关闭必须<b>逐笔调用</b>
     * {@code closeOne}，才能让每笔有自己的事务。若在同一个类里循环调用，
     * Spring 代理会被自调用绕过，{@code @Transactional} 静默失效。
     */
    java.util.List<Long> findTimeoutAftersaleIds(int limit);

    /**
     * 关闭单笔超时售后（独立事务、幂等）。
     *
     * @return true 表示本次真的关闭了（false = 状态已变，无需处理）
     */
    boolean closeOne(Long aftersaleId);
}
