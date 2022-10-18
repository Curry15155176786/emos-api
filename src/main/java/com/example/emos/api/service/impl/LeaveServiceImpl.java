package com.example.emos.api.service.impl;

import com.example.emos.api.common.util.PageUtils;
import com.example.emos.api.db.dao.TbLeaveDao;
import com.example.emos.api.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;

public class LeaveServiceImpl implements LeaveService {

    @Autowired
    private TbLeaveDao leaveDao;

    @Override
    public PageUtils searchLeaveByPage(HashMap param) {
        System.out.println("00000");
        ArrayList<HashMap> list = leaveDao.searchLeaveByPage(param);
        long count = leaveDao.searchLeaveCount(param);
        int start = (Integer) param.get("start");
        int length = (Integer) param.get("length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }
}
