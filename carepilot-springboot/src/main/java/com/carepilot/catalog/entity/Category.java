package com.carepilot.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("catalog_category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
    @TableLogic
    private Integer deleted;
}
