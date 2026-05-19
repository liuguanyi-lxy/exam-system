package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class BaseEntity implements Serializable {

//    @JsonProperty("hahaha")
    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    //@JsonFormat(pattern = "yyyy年MM月dd日 HH:mm:ss" ,timezone = "GMT+8")
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)//在插入数据时自动填充,会触发元数据处理对象的insertFill方法
    private Date createTime;

    //@JsonFormat(pattern = "yyyy年MM月dd日 HH:mm:ss" ,timezone = "GMT+8")
    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.UPDATE)//在更新数据时自动填充，会触发元数据处理对象的updateFill方法
    private Date updateTime;

    @Schema(description = "逻辑删除")
    @TableField("is_deleted")
    //@TableLogic 是 MyBatis-Plus 提供的一个注解，用于实现逻辑删除功能,在数据库中并不会真正删除数据，而是将数据的状态标记为已删除
    @TableLogic
    @JsonIgnore
    private Byte isDeleted;

}