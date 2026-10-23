/**
 * 持久层 Mapper 接口包。
 * <p>
 * 约定：
 * <ul>
 *   <li>接口命名 {@code XxxMapper}，与 {@code shopping-model} 中的实体对应；</li>
 *   <li>单表增删改查<b>不写方法</b>，直接继承 {@code BaseMapper<T>}，由 MyBatis-Plus 提供；</li>
 *   <li>多表关联、复杂聚合<b>必须手写 XML</b>，放在 {@code shopping-dao/src/main/resources/mapper/} 下，
 *       禁止用 {@code @Select} 注解拼长 SQL（可读性差、无法动态拼接）；</li>
 *   <li>所有参数一律用 {@code #{}}，禁止 {@code ${}} 拼接（防 SQL 注入）；
 *       排序字段这类无法参数化的地方必须走枚举白名单。</li>
 * </ul>
 * <p>
 * 由启动类上的 {@code @MapperScan("com.shopping.dao.mapper")} 统一扫描，
 * 因此接口上<b>不需要</b>逐个加 {@code @Mapper} 注解。
 */
package com.shopping.dao.mapper;
