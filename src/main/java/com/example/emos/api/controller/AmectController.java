package com.example.emos.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.example.emos.api.common.util.PageUtils;
import com.example.emos.api.common.util.R;
import com.example.emos.api.controller.form.*;
import com.example.emos.api.db.pojo.TbAmect;
import com.example.emos.api.exception.EmosException;
import com.example.emos.api.service.AmectService;
import com.example.emos.api.websocket.WebSocketService;
import com.example.emos.api.wxpay.WXPay;
import com.example.emos.api.wxpay.WXPayUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/amect")
@Tag(name = "AmectController", description = "罚款Web接口")
@Slf4j
public class AmectController {
    @Resource
    private AmectService amectService;

    @Value("${wx.key}")
    private String key;

    @PostMapping("/searchAmectByPage")
    @Operation(summary = "查询罚款分页记录")
    @SaCheckLogin
    public R searchAmectByPage(@Valid @RequestBody SearchAmectByPageForm form) {
        Integer page = form.getPage();
        Integer length = form.getLength();
        Integer start = (page-1) * length;
        HashMap param = JSONUtil.parse(form).toBean(HashMap.class);
        param.put("currentUserId", StpUtil.getLoginIdAsInt());
        param.put("start", start);
        if (!(StpUtil.hasPermission("AMECT:SELECT") || StpUtil.hasPermission("ROOT"))) {
            param.put("userId", StpUtil.getLoginIdAsInt());
        }
        PageUtils pageUtils = amectService.searchAmectByPage(param);
        return R.ok().put("page", pageUtils);
    }

    @PostMapping("/insert")
    @Operation(summary = "添加罚款记录")
    @SaCheckPermission(value = {"ROOT", "AMECT:INSERT"}, mode = SaMode.OR)
    public R insert(@Valid @RequestBody InsertAmectForm form) {
        ArrayList<TbAmect> list = new ArrayList<>();
        for(Integer userId : form.getUserId() ){
                TbAmect amect = new TbAmect();
                amect.setAmount(new BigDecimal(form.getAmount()));
                amect.setTypeId(form.getTypeId());
                amect.setReason(form.getReason());
                amect.setUserId(userId);
                amect.setUuid(IdUtil.simpleUUID());
                list.add(amect);
            }
                int rows = amectService.insert(list);
                return R.ok().put("rows", rows);
    }

    @PostMapping("/searchById")
    @Operation(summary = "根据ID查找罚款记录")
    @SaCheckPermission(value = {"ROOT", "AMECT:SELECT"}, mode = SaMode.OR)
    public R searchById(@Valid @RequestBody SearchAmectByIdForm form) {
        HashMap map = amectService.searchById(form.getId());
        return R.ok(map);
    }

    @PostMapping("/update")
    @Operation(summary = "更新罚款记录")
//    @SaCheckPermission(value = {"ROOT", "AMECT:UPDATE"}, mode = SaMode.OR)
    public R update(@Valid @RequestBody UpdateAmectForm form) {
        HashMap param = JSONUtil.parse(form).toBean(HashMap.class);
        int rows = amectService.update(param);
        return R.ok().put("rows", rows);
    }

    @PostMapping("/deleteAmectByIds")
    @Operation(summary = "删除罚款记录")
    @SaCheckPermission(value = {"ROOT", "AMECT:DELETE"}, mode = SaMode.OR)
    public R deleteAmectByIds(@Valid @RequestBody DeleteAmectByIdsForm form) {
        int rows = amectService.deleteAmectByIds(form.getIds());
        return R.ok().put("rows", rows);
    }

    @PostMapping("/createNativeAmectPayOrder")
    @Operation(summary = "创建Native支付罚款订单")
    @SaCheckLogin
    public R createNativeAmectPayOrder(@Valid @RequestBody CreateNativeAmectPayOrderForm form) {
        int amectId = form.getAmectId();
        int userId = StpUtil.getLoginIdAsInt();
        HashMap param = new HashMap(){{
           put("amectId", amectId);
           put("userId", userId);
        }};
        String qrCodeBase64 = amectService.createNativeAmectPayOrder(param);
        return R.ok().put("qrCodeBase64", qrCodeBase64);
    }

    @Operation(summary = "接收消息通知")
    @RequestMapping("/receiveMessage")
    public void receiveMessage(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request.setCharacterEncoding("utf-8");
        Reader reader = request.getReader();
        BufferedReader buffer = new BufferedReader(reader);
        String line = buffer.readLine();
        StringBuffer temp = new StringBuffer();
        while (line != null) {
            temp.append(line);
            line = buffer.readLine();
        }
        buffer.close();
        reader.close();
        String xml = temp.toString();
        // 利用数字证书验证收到的响应内容，避免有人伪造付款结果发送给Web方法。
        if (WXPayUtil.isSignatureValid(xml, key)) {
            Map<String, String> map = WXPayUtil.xmlToMap(temp.toString());
            String resultCode = map.get("result_code");
            String returnCode = map.get("return_code");
            if ("SUCCESS".equals(resultCode) && "SUCCESS".equals(returnCode)) {
                String outTradeNo = map.get("out_trade_no");    //罚款单UUID
                //更新订单状态
                HashMap param = new HashMap() {{
                    put("status", 2);
                    put("uuid", outTradeNo);
                }};
                int rows = amectService.updateStatus(param);
                if (rows == 1) {
                    // 利用webSocket向前端页面推送付款结果
                    //根据罚款单ID查询用户ID
                    int userId = amectService.searchUserIdByUUID(outTradeNo);
                    //向用户推送结果
                    WebSocketService.sendInfo("收款成功", userId + "");
                    //给微信平台返回响应
                    response.setCharacterEncoding("utf-8");
                    response.setContentType("application/xml");
                    Writer writer = response.getWriter();
                    BufferedWriter bufferedWriter = new BufferedWriter(writer);
                    bufferedWriter.write("<xml><return_code><![CDATA[SUCCESS]]></return_code> <return_msg><![CDATA[OK]]></return_msg></xml>");
                    bufferedWriter.close();
                    writer.close();
                } else {
                    log.error("更新订单状态失败");
                    response.sendError(500, "更新订单状态失败");
                }
            }
        } else {
            log.error("数字签名异常");
            response.sendError(500, "数字签名异常");
        }
    }


    @PostMapping("/searchNativeAmectPayResult")
    @Operation(summary = "查询Native支付罚款订单结果")
    @SaCheckLogin
    public R searchNativeAmectPayResult(@Valid @RequestBody SearchNativeAmectPayResultForm form) {
        int userId = StpUtil.getLoginIdAsInt();
        int amectId = form.getAmectId();
        HashMap param = new HashMap() {{
            put("amectId", amectId);
            put("userId", userId);
            put("status", 1);
        }};
        amectService.searchNativeAmectPayResult(param);
        return R.ok();
    }

    }


