package com.i1314i.syncerplusservice.compensator.single;

import com.alibaba.fastjson.JSON;
import com.i1314i.syncerplusservice.constant.RedisCommandTypeEnum;
import com.i1314i.syncerplusservice.entity.EventEntity;
import com.i1314i.syncerplusservice.entity.PipelineDataEntity;
import com.i1314i.syncerplusservice.entity.thread.EventTypeEntity;
import com.i1314i.syncerplusservice.service.exception.TaskMsgException;
import com.i1314i.syncerplusservice.util.Jedis.JDJedis;
import com.i1314i.syncerplusservice.util.Jedis.ObjectUtils;
import com.i1314i.syncerplusservice.util.TaskMonitorUtils;
import com.i1314i.syncerplusservice.util.TaskMsgUtils;
import com.moilioncircle.redis.replicator.Configuration;
import com.moilioncircle.redis.replicator.RedisURI;
import com.moilioncircle.redis.replicator.rdb.datatype.DB;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.params.SetParams;

import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static redis.clients.jedis.Protocol.Command.AUTH;

/**
 * pipeline补偿机制
 */
@Slf4j
public class PipelineCompensator {

    public  synchronized static boolean singleCompensator(List<Object> statusList, List<EventEntity> keys, RedisURI suri, RedisURI turi, String thredId) {
        //提交数据的总量
        int statusListLenght = statusList.size();
        int aNum=0;
        Set<EventEntity> mistakeKeys = new HashSet<>();
        for (int i = 0; i < statusList.size(); i++) {
            Object status = statusList.get(i);
            EventEntity dataEntity = keys.get(i);

            if (dataEntity.getTypeEntity().equals(EventTypeEntity.USE)) {
                aNum++;

                if (status instanceof String) {

                    if (!status.toString().trim().toUpperCase().equals("OK")) {

                        mistakeKeys.add(keys.get(i));
                    }
                } else if (status instanceof Integer) {
                    if (Integer.valueOf(status.toString()) < 0) {
                        mistakeKeys.add(keys.get(i));
                    }
                } else if (status instanceof Long) {
                    if (Long.valueOf(status.toString()) < 0) {
                        mistakeKeys.add(keys.get(i));

                    }
                } else {
                    System.out.println(JSON.toJSONString(keys.get(i)));
                }
            }

        }
            if(mistakeKeys==null||mistakeKeys.size()==0){

                return true;
            }

            //判断是否超过50%
            if (mistakeKeys.size() <= (aNum/2)) {


                JDJedis sJdJedis=new JDJedis(suri.getHost(),suri.getPort());

                JDJedis tJdJedis=new JDJedis(turi.getHost(),turi.getPort());
                Configuration tconfig = Configuration.valueOf(turi);
                Configuration sconfig = Configuration.valueOf(suri);

                try {
                    if (sconfig.getAuthPassword() != null) {
                        Object auth =sJdJedis.sendCommand(AUTH, tconfig.getAuthPassword().getBytes());
                    }

                    if (tconfig.getAuthPassword() != null) {
                        Object auth =tJdJedis.sendCommand(AUTH, tconfig.getAuthPassword().getBytes());
                    }
                }catch (Exception e){
                    try {
                        log.info("[{}]补偿机制获取链接失败",thredId);
                        Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                    } catch (TaskMsgException ex) {
                        e.printStackTrace();
                    }

                }


                for (EventEntity eventEntity:mistakeKeys
                     ) {

                    if( tJdJedis.getDbNum()!=eventEntity.getDb().getDbNumber()){
                        tJdJedis.select((int) eventEntity.getDb().getDbNumber());
                    }

                    try {
                        StringConpensator(eventEntity.getRedisCommandTypeEnum(), tJdJedis, sJdJedis, eventEntity);
                    } catch (Exception e) {

                        try {
                            Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                        } catch (TaskMsgException ex) {
                            e.printStackTrace();
                        }
                       break;

                    }
                    try {
                        SetConpensator(eventEntity.getRedisCommandTypeEnum(), tJdJedis, sJdJedis, eventEntity);
                    } catch (Exception e) {
                        try {
                            Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                        } catch (TaskMsgException ex) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    try {
                        ZSetConpensator(eventEntity.getRedisCommandTypeEnum(), tJdJedis, sJdJedis, eventEntity);
                    } catch (Exception e) {
                        try {
                            Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                        } catch (TaskMsgException ex) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    try {
                        StringConpensator(eventEntity.getRedisCommandTypeEnum(), tJdJedis, sJdJedis, eventEntity);
                    } catch (Exception e) {
                        try {
                            Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                        } catch (TaskMsgException ex) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    try {
                        ListConpensator(eventEntity.getRedisCommandTypeEnum(), tJdJedis, sJdJedis, eventEntity);
                    } catch (Exception e) {
                        try {
                            Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                        } catch (TaskMsgException ex) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    try {
                        HashConpensator(eventEntity.getRedisCommandTypeEnum(), tJdJedis, sJdJedis, eventEntity);
                    } catch (Exception e) {
                        try {
                            Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                        } catch (TaskMsgException ex) {
                            e.printStackTrace();
                        }
                        break;
                    }

                    /**
                    if(eventEntity.getRedisCommandTypeEnum().equals(RedisCommandTypeEnum.DUMP)){
                        if(eventEntity.getMs()!=0L){
                           Object result= tJdJedis.set(eventEntity.getKey(),sJdJedis.get(eventEntity.getKey()), new SetParams().px(eventEntity.getMs()));
                            if(!result.equals("OK")){
                                try {
                                    Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                                } catch (TaskMsgException e) {
                                    e.printStackTrace();
                                }

                                break;
                            }
                        }else {
                            Object result=tJdJedis.set(eventEntity.getKey(),sJdJedis.get(eventEntity.getKey()));
                            if(!result.equals("OK")){
                                try {
                                    Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                                } catch (TaskMsgException e) {
                                    e.printStackTrace();
                                }

                                break;
                            }
                        }

                    }else if(eventEntity.getRedisCommandTypeEnum().equals(RedisCommandTypeEnum.LIST)){


                    }

                     **/
                }

                sJdJedis.close();
                tJdJedis.close();
//                if()



//                System.out.println(thredId);

//                try {
//                    Map<String, String> msg = TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
//                } catch (TaskMsgException e) {
//                    e.printStackTrace();
//                }
//                log.warn("任务Id【{}】异常停止，停止原因【{}】", taskId, er.getMessage());
                //task panic

            }else{
                //结束当前线程停止同步
                try {
                    TaskMsgUtils.brokenCreateThread(Arrays.asList(thredId));
                } catch (TaskMsgException e) {
                    e.printStackTrace();
                }

                log.info("[{}]补偿机制",thredId);
            }

        // 逐条提交 有错误 task panic
//        for (PipelineDataEntity pipelineData :
//                mistakeKeys) {
//
//            //提交返回结果
//            //如有错误直接停止
//            if (true) {
//                //task panic
//                return false;
//            }
//        }


        return true;
    }


    public static String StringConpensator(RedisCommandTypeEnum redisCommandTypeEnum, JDJedis target, JDJedis source, EventEntity eventEntity) throws Exception {
        String msg=null;

        if (redisCommandTypeEnum.equals(RedisCommandTypeEnum.STRING)) {

            if (eventEntity.getMs() != 0L) {

                msg=target.set(eventEntity.getKey(), source.get(eventEntity.getKey()), new SetParams().px(eventEntity.getMs()));
            } else {
                msg=target.set(eventEntity.getKey(),  source.get(eventEntity.getKey()));

            }

        }else{

            return null;
        }



        if(!msg.trim().toUpperCase().equals("OK")){

            throw new Exception("broken");
        }
        return msg;
    }

    public static Long SetConpensator(RedisCommandTypeEnum redisCommandTypeEnum, JDJedis target, JDJedis source, EventEntity eventEntity) throws Exception {
        long msg = -1;
        if (redisCommandTypeEnum.equals(RedisCommandTypeEnum.SET)) {
            if (eventEntity.getMs() != 0L) {
                msg=target.sadd(eventEntity.getKey(), ObjectUtils.setBytes(source.smembers(eventEntity.getKey())));
                target.pexpire(eventEntity.getKey(), eventEntity.getMs());
            } else {
                msg=target.sadd(eventEntity.getKey(), ObjectUtils.setBytes(source.smembers(eventEntity.getKey())));
            }
        }else
            return null;
        if(msg<0){
            throw new Exception("broken");
        }
        return msg;
    }


    public static Long ZSetConpensator(RedisCommandTypeEnum redisCommandTypeEnum, JDJedis target, JDJedis source, EventEntity eventEntity) throws Exception {
        long msg = -1;
        if (redisCommandTypeEnum.equals(RedisCommandTypeEnum.ZSET)) {
            if (eventEntity.getMs() != 0L) {
                    msg=target.zadd(eventEntity.getKey(), ObjectUtils.zsetByteP(source.zrange(eventEntity.getKey(), 0, source.zcard(eventEntity.getKey())),source,eventEntity.getKey()));

                    target.pexpire(eventEntity.getKey(), eventEntity.getMs());
                } else {

                    msg=target.zadd(eventEntity.getKey(), ObjectUtils.zsetByteP(source.zrange(eventEntity.getKey(), 0, source.zcard(eventEntity.getKey())),source,eventEntity.getKey()));
                }
        }else
            return null;
        if(msg<0){
            throw new Exception("broken");
        }
        return msg;
    }

    public static String HashConpensator(RedisCommandTypeEnum redisCommandTypeEnum, JDJedis target, JDJedis source, EventEntity eventEntity) throws Exception {
        String msg=null;
        if (redisCommandTypeEnum.equals(RedisCommandTypeEnum.HASH)) {
            if (eventEntity.getMs() != 0L) {
                msg=target.hmset(eventEntity.getKey(), source.hgetAll(eventEntity.getKey()));
                target.pexpire(eventEntity.getKey(), eventEntity.getMs());
            } else {
                msg=target.hmset(eventEntity.getKey(), source.hgetAll(eventEntity.getKey()));
            }
        }else
            return null;
        if(!msg.trim().toUpperCase().equals("OK")){
            throw new Exception("broken");
        }
        return msg;
    }

    public static Long ListConpensator(RedisCommandTypeEnum redisCommandTypeEnum, JDJedis target, JDJedis source,EventEntity eventEntity) throws Exception {
        long msg = -1;
        if (redisCommandTypeEnum.equals(RedisCommandTypeEnum.LIST)) {
            if (eventEntity.getMs() != 0L) {
                msg=target.lpush(eventEntity.getKey(), ObjectUtils.listBytes(source.lrange(eventEntity.getKey(), 0, source.llen(eventEntity.getKey()))));
                target.pexpire(eventEntity.getKey(), eventEntity.getMs());
            } else {
                msg=target.lpush(eventEntity.getKey(), ObjectUtils.listBytes(source.lrange(eventEntity.getKey(), 0, source.llen(eventEntity.getKey()))));
            }
        }else
            return null;
        if(msg<0){
            throw new Exception("broken");
        }
        return msg;

    }


    public static void main(String[] args) throws URISyntaxException {
        List<Object> listResult = Stream.of("OK", "error").collect(Collectors.toList());
//        JDJedis source=new JDJedis("127.0.0.1",6379);
        RedisURI sui=new RedisURI("redis://127.0.0.1:6379?authPassword=");
        RedisURI tui=new RedisURI("redis://114.67.100.239:6379?authPassword=redistest0102");
//        System.out.println(source.ping());
//        JDJedis target=new JDJedis("114.67.100.239",6379);
//        target.auth("redistest0102");
        List<EventEntity> listResultS = Stream.of(new EventEntity("test".getBytes(), 0, new DB(0),EventTypeEntity.USE,RedisCommandTypeEnum.STRING),
                new EventEntity("test1".getBytes(), 0, new DB(0),EventTypeEntity.USE,RedisCommandTypeEnum.STRING)).collect(Collectors.toList());

        singleCompensator(listResult,listResultS,sui,tui,"test");
    }
}