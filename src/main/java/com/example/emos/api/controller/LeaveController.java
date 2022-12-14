package com.example.emos.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.example.emos.api.common.util.PageUtils;
import com.example.emos.api.common.util.R;
import com.example.emos.api.controller.form.SearchLeaveByPageForm;
import com.example.emos.api.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;

@RestController
@RequestMapping("/leave")
@Tag(name = "LeaveController", description = "员工请假Web接口")
public class LeaveController {
    @Resource
    private LeaveService leaveService;

    @PostMapping("/searchLeaveByPage")
    @Operation(summary = "查询请假分页数据")
    @SaCheckLogin
    public R searchLeaveByPage(@Valid @RequestBody SearchLeaveByPageForm form) {
         System.out.println("pull test");
        System.out.println("master commit");
        System.out.println("hot-fix");
        int page = form.getPage();
        int length = form.getLength();
        int start = (page - 1) * length;
        HashMap param = JSONUtil.parse(form).toBean(HashMap.class);
        param.put("start", start);
        param.put("myId", StpUtil.getLoginIdAsInt());
        if (!(StpUtil.hasPermission("LEAVE:SELECT")||StpUtil.hasPermission("ROOT"))) {
            param.put("userId", StpUtil.getLoginIdAsInt());
        }
        PageUtils pageUtils = leaveService.searchLeaveByPage(param);
        return R.ok().put("page", pageUtils);
    }
}
