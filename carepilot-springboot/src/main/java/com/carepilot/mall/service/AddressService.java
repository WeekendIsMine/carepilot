package com.carepilot.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.common.exception.BizException;
import com.carepilot.mall.dto.AddressRequest;
import com.carepilot.mall.entity.MallAddress;
import com.carepilot.mall.mapper.MallAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址。第一条自动设默认；设默认时先清掉其它默认，保证只有一条。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final MallAddressMapper mapper;

    /** 我的地址，默认地址排最前 */
    public List<MallAddress> list(Long userId) {
        return mapper.selectList(Wrappers.<MallAddress>lambdaQuery()
                .eq(MallAddress::getUserId, userId)
                .orderByDesc(MallAddress::getIsDefault)
                .orderByDesc(MallAddress::getId));
    }

    /** 新增；没有地址时自动变成默认 */
    @Transactional
    public MallAddress create(Long userId, AddressRequest req) {
        boolean first = list(userId).isEmpty();
        if (req.isDefaultAddress() || first) {
            clearDefault(userId);
        }
        MallAddress a = new MallAddress();
        a.setUserId(userId);
        a.setReceiverName(req.getReceiverName());
        a.setReceiverPhone(req.getReceiverPhone());
        a.setProvince(req.getProvince());
        a.setCity(req.getCity());
        a.setDistrict(req.getDistrict());
        a.setDetailAddress(req.getDetailAddress());
        a.setIsDefault(req.isDefaultAddress() || first ? 1 : 0);
        mapper.insert(a);
        return a;
    }

    /** 修改；原本是默认的继续是默认 */
    @Transactional
    public MallAddress update(Long userId, Long id, AddressRequest req) {
        MallAddress a = require(userId, id);
        boolean makeDefault = req.isDefaultAddress() || Integer.valueOf(1).equals(a.getIsDefault());
        if (makeDefault) {
            clearDefault(userId);
        }
        a.setReceiverName(req.getReceiverName());
        a.setReceiverPhone(req.getReceiverPhone());
        a.setProvince(req.getProvince());
        a.setCity(req.getCity());
        a.setDistrict(req.getDistrict());
        a.setDetailAddress(req.getDetailAddress());
        a.setIsDefault(makeDefault ? 1 : 0);
        mapper.updateById(a);
        return mapper.selectById(id);
    }

    /** 设为默认：先清空其它默认再置位 */
    @Transactional
    public void setDefault(Long userId, Long id) {
        require(userId, id);
        clearDefault(userId);
        MallAddress patch = new MallAddress();
        patch.setId(id);
        patch.setIsDefault(1);
        mapper.updateById(patch);
    }

    /** 删除，按归属校验 */
    public void delete(Long userId, Long id) {
        require(userId, id);
        mapper.deleteById(id);
    }

    /** 取地址并校验归属，下单时用 */
    public MallAddress require(Long userId, Long id) {
        MallAddress a = mapper.selectById(id);
        if (a == null || !a.getUserId().equals(userId)) {
            throw BizException.notFound("收货地址不存在");
        }
        return a;
    }

    private void clearDefault(Long userId) {
        MallAddress patch = new MallAddress();
        patch.setIsDefault(0);
        mapper.update(patch, Wrappers.<MallAddress>lambdaUpdate()
                .eq(MallAddress::getUserId, userId)
                .eq(MallAddress::getIsDefault, 1));
    }
}