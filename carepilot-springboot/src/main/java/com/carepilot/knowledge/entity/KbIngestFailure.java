package com.carepilot.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_ingest_failure")
public class KbIngestFailure {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String documentTitle;
    private String error;
    private Integer attempts;

    private Integer resolved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
