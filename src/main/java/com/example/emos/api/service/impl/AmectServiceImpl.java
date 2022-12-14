package com.example.emos.api.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.alibaba.druid.wall.WallSQLException;
import com.example.emos.api.common.util.PageUtils;
import com.example.emos.api.db.dao.TbAmectDao;
import com.example.emos.api.db.pojo.TbAmect;
import com.example.emos.api.exception.EmosException;
import com.example.emos.api.service.AmectService;
import com.example.emos.api.wxpay.MyWXPayConfig;
import com.example.emos.api.wxpay.WXPay;
import com.example.emos.api.wxpay.WXPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AmectServiceImpl implements AmectService {
    @Resource
    private TbAmectDao amectDao;

    @Autowired
    private MyWXPayConfig myWXPayConfig;
    @Override
    public PageUtils searchAmectByPage(HashMap param) {
        ArrayList<HashMap> list = amectDao.searchAmectByPage(param);
        long count = amectDao.searchAmectCount(param);
        int start = (Integer) param.get("start");
        int length = (Integer) param.get("length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }

    @Override
    public int insert(ArrayList<TbAmect> list) {
        list.forEach(one -> {
            amectDao.insert(one);
        });
        return list.size();
    }

    @Override
    public HashMap searchById(int id) {
        HashMap map = amectDao.searchById(id);
        return map;
    }

    @Override
    public int update(HashMap param) {
        int rows = amectDao.update(param);
        return rows;
    }

    @Override
    public int deleteAmectByIds(Integer[] ids) {
        int rows = amectDao.deleteAmectByIds(ids);
        return rows;
    }

    @Override
    public String createNativeAmectPayOrder(HashMap param) {
        int userId = MapUtil.getInt(param, "userId");
        int amectId = MapUtil.getInt(param, "amectId");
        //???????????????ID?????????ID?????????????????????
        HashMap map = amectDao.searchAmectByCondition(param);
        if (map != null && map.size() > 0) {
            String amount = new BigDecimal(MapUtil.getStr(map, "amount")).multiply(new BigDecimal("100")).intValue() + "";
            try {
                WXPay wxPay = new WXPay(myWXPayConfig);
                param.clear();
                param.put("nonce_str", WXPayUtil.generateNonceStr()); //???????????????
                param.put("body", "????????????");
                param.put("out_trade_no", MapUtil.getStr(map, "uuid"));
                param.put("total_fee", amount);
                param.put("spbill_create_ip", "127.0.0.1");
                param.put("notify_url", "http://s0.z100.vip:4234/emos-api/amect/receiveMessage");
                param.put("trade_type", "NATIVE");
                String sign = WXPayUtil.generateSignature(param, myWXPayConfig.getKey());
                param.put("sign", sign);
                Map<String, String> result = wxPay.unifiedOrder(param); //??????????????????
                String prepayId = result.get("prepay_id");  //????????????ID
                String codeUrl = result.get("code_url");   //???????????????????????????????????????????????????
                if (prepayId != null) {
                    param.clear();
                    param.put("prepayId", prepayId);
                    param.put("amectId", amectId);
                    int rows = amectDao.updatePrepayId(param);
                    if (rows != 1) {
                        throw new EmosException("??????????????????????????????ID??????");
                    }

                    //??????????????????URL???????????????
                    QrConfig qrConfig = new QrConfig();
                    qrConfig.setWidth(255);
                    qrConfig.setHeight(255);
                    qrConfig.setMargin(2);
                    String qrCodeBase64 = QrCodeUtil.generateAsBase64(codeUrl, qrConfig, "jpg");
                    return qrCodeBase64;
                } else {
                    log.error("????????????????????????", result);
                    throw new EmosException(result.get("err_code_des"));
                }
            } catch (Exception e) {
                log.error("????????????????????????", e);
                throw new EmosException("????????????????????????");
            }

        } else {
            throw new EmosException("?????????????????????");
        }
    }

    @Override
    public int updateStatus(HashMap param) {
        int rows = amectDao.updateStatus(param);
        return rows;
    }

    @Override
    public int searchUserIdByUUID(String uuid) {
        return amectDao.searchUserIdByUUID(uuid);
    }

    @Override
    public void searchNativeAmectPayResult(HashMap param) {
        HashMap map = amectDao.searchAmectByCondition(param);
        if (MapUtil.isNotEmpty(map)) {
            String uuid = MapUtil.getStr(map, "uuid");
            param.clear();
            param.put("appid", myWXPayConfig.getAppID());
            param.put("mch_id", myWXPayConfig.getMchID());
            param.put("out_trade_no", uuid);
            param.put("nonce_str", WXPayUtil.generateNonceStr());
            try {
                String sign = WXPayUtil.generateSignature(param, myWXPayConfig.getKey());
                param.put("sign", sign);
                WXPay wxPay = new WXPay(myWXPayConfig);
                Map<String, String> result = wxPay.orderQuery(param);
                String returnCode = result.get("return_code");
                String resultCode = result.get("result_code");
                if ("SUCCESS".equals(returnCode) && "SUCCESS".equals(resultCode)) {
                    String tradeState = result.get("trade_state");
                    //???????????????????????????
                    if ("SUCCESS".equals(tradeState)) {
                        //??????????????????
                        amectDao.updateStatus(new HashMap() {{
                            put("uuid", uuid);
                            put("status", 2);
                        }});
                    }
                }
            } catch (Exception e) {
                log.error("????????????", e);
                throw new EmosException("????????????");
            }
        }
    }


    }



