package com.carepilot.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.common.api.Pages;
import com.carepilot.common.exception.BizException;
import com.carepilot.knowledge.entity.KbDocument;
import com.carepilot.knowledge.entity.KbIngestFailure;
import com.carepilot.knowledge.mapper.KbDocumentMapper;
import com.carepilot.knowledge.mapper.KbIngestFailureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 死信补偿：记录解析失败的消息，提供列表和手动重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbFailureService {

    private final KbIngestFailureMapper failureMapper;
    private final KbDocumentMapper documentMapper;
    private final KbService kbService;

    /** 失败记录分页 */
    public IPage<KbIngestFailure> list(Integer resolved, Integer page, Integer size) {
        return failureMapper.selectPage(Pages.of(page, size),
                Wrappers.<KbIngestFailure>lambdaQuery()
                        .eq(resolved != null, KbIngestFailure::getResolved, resolved)
                        .orderByDesc(KbIngestFailure::getId));
    }

    /** 手动重试：重置文档状态并重新投递解析 */
    @Transactional
    public void retry(Long failureId) {
        KbIngestFailure f = failureMapper.selectById(failureId);
        if (f == null) throw BizException.notFound("失败记录不存在");
        KbDocument doc = documentMapper.selectById(f.getDocumentId());
        if (doc == null) throw BizException.notFound("对应文档已删除，无法重试");

        KbDocument patch = new KbDocument();
        patch.setId(doc.getId());
        patch.setStatus("PENDING");
        patch.setParseError(null);
        documentMapper.updateById(patch);

        kbService.dispatch(doc.getId());

        KbIngestFailure done = new KbIngestFailure();
        done.setId(f.getId());
        done.setResolved(1);
        failureMapper.updateById(done);
        log.info("kb ingest retry requested, doc={} failure={}", doc.getId(), failureId);
    }
}