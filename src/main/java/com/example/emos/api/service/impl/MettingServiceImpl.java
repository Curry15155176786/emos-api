package com.example.emos.api.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.example.emos.api.common.util.PageUtils;
import com.example.emos.api.common.util.R;
import com.example.emos.api.db.dao.TbMeetingDao;
import com.example.emos.api.db.dao.TbUserDao;
import com.example.emos.api.db.pojo.TbMeeting;
import com.example.emos.api.exception.EmosException;
import com.example.emos.api.service.MeetingService;
import com.example.emos.api.task.MeetingWorkflowTask;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;

@Service
public class MettingServiceImpl implements MeetingService {
    @Resource
    private TbMeetingDao tbMeetingDao;

    @Resource
    private TbMeetingDao meetingDao;

    @Resource
    private MeetingWorkflowTask meetingWorkflowTask;

    @Autowired
    private RedisTemplate redisTemplate;
    @Override
    public PageUtils searchOfflineMeetingByPage(HashMap param) {
        int length = (Integer) param.get("length");
        int start = (Integer) param.get("start");
        long count = tbMeetingDao.searchOfflineMeetingCount(param);
        ArrayList<HashMap> list = tbMeetingDao.searchOfflineMeetingByPage(param);
        for(HashMap hashMap : list){
            String meeting = (String) hashMap.get("meeting");
            if( !StringUtil.isEmpty(meeting) && meeting.length() > 0){
                hashMap.put("meeting", JSONUtil.parseArray(meeting));
            }
        }
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }

    @Override
    public int insert(TbMeeting meeting) {
        int rows = meetingDao.insert(meeting);
        if (rows != 1) {
            throw new EmosException("??????????????????");
        }
        meetingWorkflowTask.startMeetingWorkflow(meeting.getUuid(), meeting.getCreatorId(), meeting.getTitle(),
                meeting.getDate(), meeting.getStart() + ":00", meeting.getType() == 1 ? "????????????" : "????????????");
        return rows;
    }


    @Override
    public ArrayList<HashMap> searchOfflineMeetingInWeek(HashMap param) {
        ArrayList<HashMap> list = meetingDao.searchOfflineMeetingInWeek(param);
        return list;
    }

    @Override
    public HashMap searchMeetingInfo(short status, long id) {
        //??????????????????????????????
        HashMap map;
        //???????????????????????????????????????????????????????????????????????????present???unpresent??????
        if (status == 4||status==5) {
            // ??????????????????????????????????????????????????????????????????????????????
            map = meetingDao.searchCurrentMeetingInfo(id);
        } else {
            // ??????????????????????????????
            map = meetingDao.searchMeetingInfo(id);
        }
        return map;
    }

    @Override
    public int deleteMeetingApplication(HashMap param) {
        Long id = MapUtil.getLong(param, "id");
        String uuid = MapUtil.getStr(param, "uuid");
        String instanceId = MapUtil.getStr(param, "instanceId");
        //?????????????????????????????????????????????????????????????????????20??????
        HashMap meeting = meetingDao.searchMeetingById(param);
        String date = MapUtil.getStr(meeting, "date");
        String start = MapUtil.getStr(meeting, "start");
        int status = MapUtil.getInt(meeting, "status");
        boolean isCreator = Boolean.parseBoolean(MapUtil.getStr(meeting, "isCreator"));
        DateTime dateTime = DateUtil.parse(date + " " + start);
        DateTime now = DateUtil.date();

        //????????????????????????20???????????????????????????
        if (now.isAfterOrEquals(dateTime.offset(DateField.MINUTE, -20))) {
            throw new EmosException("????????????????????????20???????????????????????????");
        }
        //??????????????????????????????
        if (!isCreator) {
            throw new EmosException("??????????????????????????????");
        }
        //??????????????????????????????????????????
        if (status == 1 || status == 3) {
            int rows = meetingDao.deleteMeetingApplication(param);
            if (rows == 1) {
                String reason = param.get("reason").toString();
                meetingWorkflowTask.deleteMeetingApplication(uuid, instanceId, reason);
            }
            return rows;
        } else {
            throw new EmosException("??????????????????????????????????????????");
        }
    }

    @Override
    public Long searchRoomIdByUUID(String uuid) {
        if(redisTemplate.hasKey(uuid)){
            Object temp = redisTemplate.opsForValue().get(uuid);
            long roodId = Long.parseLong(temp.toString());
            return roodId;
        }
        return null;
    }

    @Override
    public ArrayList<HashMap> searchOnlineMeetingMembers(HashMap param) {
        ArrayList<HashMap> list = meetingDao.searchOnlineMeetingMembers(param);
        return list;
    }

    @Override
    public PageUtils searchOnlineMeetingByPage(HashMap param) {
        ArrayList<HashMap> list = meetingDao.searchOnlineMeetingByPage(param);
        long count = meetingDao.searchOnlineMeetingCount(param);
        int start = (Integer) param.get("start");
        int length = (Integer) param.get("length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }

    @Override
    public boolean searchCanCheckinMeeting(HashMap param) {
        long count = meetingDao.searchCanCheckinMeeting(param);
        return count == 1 ? true : false;
    }

    @Override
    public int updateMeetingPresent(HashMap param) {
        int rows = meetingDao.updateMeetingPresent(param);
        return rows;
    }


}
