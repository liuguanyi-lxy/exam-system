package com.atguigu.exam.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色要求注解
 * 标注在 Controller 类或方法上，由 AuthInterceptor 统一校验当前登录用户的角色；
 * 方法级注解优先于类级注解。角色取值与 users.role 一致：ADMIN / TEACHER / STUDENT
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色列表，命中其一即放行 */
    String[] value();
}
