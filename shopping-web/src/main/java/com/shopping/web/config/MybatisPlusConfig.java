package com.shopping.web.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 三个拦截器的顺序很重要：<b>分页必须放在最后一个</b>。因为其它拦截器如果需要
 * 改写 SQL（比如多租户、动态表名），必须先改完再分页，否则分页的 count 语句会算错。
 * 官方文档明确要求「使用多个功能时，建议将分页插件放到最后」。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // ① 乐观锁：配合实体上的 @Version 字段，用 version 做 CAS 更新
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // ② 防全表更新/删除：拦截没有 where 条件的 update/delete
        //    这条是保命的——一条 `update oms_order set status = 1` 忘写 where 就是生产事故
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // ③ 分页：必须最后加
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限，防止前端传 size=100000 拖垮数据库
        pagination.setMaxLimit(100L);
        // 溢出总页数后是否进行处理（true = 回到首页，false = 返回空）
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
