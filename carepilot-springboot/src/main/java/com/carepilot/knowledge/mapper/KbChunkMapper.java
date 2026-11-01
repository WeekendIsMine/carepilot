package com.carepilot.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carepilot.knowledge.entity.KbChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface KbChunkMapper extends BaseMapper<KbChunk> {

    @Delete("DELETE FROM kb_chunk WHERE document_id = #{documentId}")
    int hardDeleteByDocument(@Param("documentId") Long documentId);
}
