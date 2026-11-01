package com.carepilot.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_chunk")
public class KbChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkNo;
    private Integer parentNo;
    private String titlePath;
    private String content;

    private String parentContent;
    private Integer tokenCount;
    private String embeddingModel;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
}
