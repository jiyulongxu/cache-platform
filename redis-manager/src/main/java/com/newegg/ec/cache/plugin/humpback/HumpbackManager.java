package com.newegg.ec.cache.plugin.humpback;

import com.google.common.collect.Lists;
import com.newegg.ec.cache.app.model.User;
import com.newegg.ec.cache.app.util.HttpClientUtil;
import com.newegg.ec.cache.app.util.JedisUtil;
import com.newegg.ec.cache.app.util.RequestUtil;
import com.newegg.ec.cache.core.logger.CommonLogger;
import com.newegg.ec.cache.plugin.INodeOperate;
import com.newegg.ec.cache.plugin.INodeRequest;
import com.newegg.ec.cache.plugin.basemodel.Node;
import com.newegg.ec.cache.plugin.basemodel.StartType;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by lzz on 2018/4/20.
 */
@Component
public class HumpbackManager implements INodeOperate,INodeRequest {

    CommonLogger logger = new CommonLogger( HumpbackManager.class );

    static ExecutorService executorService = Executors.newFixedThreadPool(100);
    @Value("${cache.humpback.image}")
    private String humpbackImage;
    @Value("${cache.humpback.api.format}")
    private String humpbackApiFormat;

    private static final String CONTAINER_OPTION_API = "containers";
    private static final String IMAGE_OPTION_API = "images";

    @Autowired
    IHumpbackNodeDao humpbackNodeDao;

    public HumpbackManager(){

    }

    @Override
    public boolean pullImage(JSONObject pullParam) {
        String ipStr = pullParam.getString("iplist");
        Set<String> ipSet = JedisUtil.getIPList(ipStr);
        String imageUrl = pullParam.getString("imageUrl");
        List<Future<Boolean>> futureList = new ArrayList<>();
        for(String ip : ipSet){
            Future<Boolean> future = executorService.submit( new PullImageTask(ip, imageUrl) );
            futureList.add( future );
        }
        for(Future<Boolean> future : futureList){
            try {
                future.get();
            } catch (Exception e) {

            }
        }
        return false;
    }

    @Override
    public boolean install(JSONObject installParam) {
        //1.pull image
        //2.create container
        //3.add master to cluster
        //4.init master (slot 分配)
        //5.add slave to cluster
        //6.be slave(设置slave的身份)


        return false;
    }

    @Override
    public boolean start(JSONObject startParam) {
        String ip = startParam.getString("ip");
        String containerId = startParam.getString("containerId");
        return optionContainer(ip, containerId, StartType.start);
    }

    @Override
    public boolean stop(JSONObject stopParam) {
        String ip = stopParam.getString("ip");
        String containerId = stopParam.getString("containerId");
        return optionContainer(ip, containerId, StartType.stop);
    }

    @Override
    public boolean restart(JSONObject restartParam) {
        stop(restartParam);
        start(restartParam);
        return true;
    }

    @Override
    public boolean remove(JSONObject removePram) {
        System.out.println( removePram );
        logger.websocket( removePram.toString() );
        String ip = removePram.getString("ip");
        String containerId = removePram.getString("containerId");
        try {
            String url = getApiAddress( ip );
            if( HttpClientUtil.getDeleteResponse(url, containerId ) == null) {
                return false;
            }
        } catch (IOException e) {
            logger.error("", e);
            return false;
        }
        return true;
    }

    @Override
    public List<String> getImageList() {
        User user  = RequestUtil.getUser();
        System.out.println( user );
        return Lists.newArrayList(humpbackImage.split(","));
    }

    @Override
    public List<Node> getNodeList(int clusterId) {
        List<Node> list = humpbackNodeDao.getHumbackNodeList(clusterId);
        return list;
    }

    @Override
    public String showInstall() {
        return "plugin/humpback/humpbackCreateCluster";
    }

    @Override
    public String showManager() {
        return "plugin/humpback/humpbackNodeManager";
    }

    public String getApiAddress(String ip){
        return String.format( humpbackApiFormat,  ip);
    }

    class PullImageTask implements Callable<Boolean> {
        private String image;
        private String ip;
        public PullImageTask(String ip, String image){
            this.ip = ip;
            this.image = image;
        }

        @Override
        public Boolean call() throws Exception {
            boolean res = true;
            try {
                JSONObject reqObject = new JSONObject();
                reqObject.put("Image", this.image);
                String url = getApiAddress(ip)+IMAGE_OPTION_API;
                HttpClientUtil.getPostResponse(url, reqObject);
            }catch (Exception e){
                res = false;
            }
            return res;
        }
    }

    /**
     * container option
     * @param ip
     * @param containerName
     * @param startType
     * @return
     */
    public boolean optionContainer(String ip,String containerName, StartType startType) {
        JSONObject object = new JSONObject();
        object.put("Action",startType);
        object.put("Container",containerName);
        try {
            String url = getApiAddress(ip) + CONTAINER_OPTION_API;
            if(HttpClientUtil.getPutResponse(url, object)==null){
                return false;
            }
        } catch (IOException e) {
            logger.error("", e);
            return false;
        }
        return true;
    }


}
