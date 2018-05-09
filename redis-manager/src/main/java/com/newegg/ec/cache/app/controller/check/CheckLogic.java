package com.newegg.ec.cache.app.controller.check;

import com.newegg.ec.cache.app.dao.IClusterDao;
import com.newegg.ec.cache.app.model.Cluster;
import com.newegg.ec.cache.app.model.Host;
import com.newegg.ec.cache.app.model.Response;
import com.newegg.ec.cache.app.model.User;
import com.newegg.ec.cache.app.util.JedisUtil;
import com.newegg.ec.cache.app.util.NetUtil;
import com.newegg.ec.cache.app.util.RequestUtil;
import com.newegg.ec.cache.core.logger.CommonLogger;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Created by gl49 on 2018/4/22.
 */
@Component
public class CheckLogic {
    public static CommonLogger logger = new CommonLogger(CheckLogic.class);
    @Resource
    private IClusterDao clusterDao;

    private String checkLog(String msg){
        return logger.websocket(msg) + "<br>";
    }

    public int checkRedisVersion(String address) {
        Host host = NetUtil.getHostPassAddress( address );
        int version = JedisUtil.getRedisVersion(host.getIp(), host.getPort());
        return version;
    }

    public Response checkPortPass(String ip, int port, boolean isPass){
        boolean res =NetUtil.checkIpAndPort(ip, port);
        if( isPass ){
            return res ? Response.Success() : Response.Error("port is not pass");
        }else{
            return !res ? Response.Success() : Response.Error("port is already use");
        }
    }

    public Response checkIp(String ip){
        boolean res = NetUtil.checkIp(ip, 5000);
        return res ? Response.Success() : Response.Error("ip is not access");
    }

    public Response checkAddress(String address) {
        Host host = NetUtil.getHostPassAddress( address );
        if( null != host ){
            return Response.Success();
        }else{
            return Response.Error("address is not access");
        }
    }

    public Response checkClusterNameByUserid(String clusterId, int id) {
        return Response.Success();
    }

    public  Response checkBatchHostNotPass(String iplist) {
        String errorMsg = "";
        String[] ipArr = iplist.split("\n");
        for(String line : ipArr) {
            try {
                checkLog("start check " + line );
                String[] tmpArr = line.split(":");
                if (tmpArr.length >= 2) {
                    String ip = tmpArr[0].trim();
                    if( !NetUtil.checkIp(ip, 5000) ){
                        errorMsg += checkLog( ip + " is not pass");
                        continue;
                    }
                    int port = Integer.parseInt(tmpArr[1].trim());
                    if( !NetUtil.checkIpAndPort(ip, port) ){
                        checkLog( line + " is ok");
                    }else{
                        errorMsg += checkLog( line + " the port is alreay use" );
                    }
                }else{
                    errorMsg += checkLog( line + " is format error" );
                }
            }catch (Exception e){
                checkLog( e.getMessage() );
            }
        }
        if( !StringUtils.isBlank( errorMsg) ){
            System.out.println( errorMsg );
            return Response.Error( errorMsg );
        }else{
            return Response.Success();
        }
    }
}
