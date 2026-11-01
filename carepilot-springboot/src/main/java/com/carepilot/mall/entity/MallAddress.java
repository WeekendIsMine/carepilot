package com.carepilot.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mall_address")
public class MallAddress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Integer isDefault;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String fullAddress() {
        return String.join("", nullSafe(province), nullSafe(city), nullSafe(district), nullSafe(detailAddress));
    }

    private String nullSafe(String s) { return s == null ? "" : s; }
}
