package com.atguigu.exam.metahandlers;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MyBatis Plus 元数据对象处理器
 * 用于在插入和更新操作时自动填充字段值,例如创建时间、更新时间等
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入操作时自动填充字段
     * 当执行插入操作时，自动填充 createTime 字段为当前时间
     *
     * @param metaObject 元数据对象，包含要操作的实体对象信息
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("开始插入填充...");
        this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
    }

    /**
     * 更新操作时自动填充字段
     * 当执行更新操作时，自动填充 updateTime 字段为当前时间
     *
     * @param metaObject 元数据对象，包含要操作的实体对象信息
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("开始更新填充...");
        this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
    }
}