package com.carepilot.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class KbDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String category;

    private String sourceType;
    private String fileUrl;

    private String ossUrl;
    private String fileName;

    private String fileHash;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private String status;
    private Integer chunkCount;
    private String parseError;
    private Long createdBy;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}