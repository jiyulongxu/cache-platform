package com.newegg.ec.cache.plugin.docker;

import com.google.common.collect.Lists;
import com.newegg.ec.cache.app.controller.check.CheckLogic;
import com.newegg.ec.cache.app.model.RedisNode;
import com.newegg.ec.cache.app.model.Response;
import com.newegg.ec.cache.app.util.DateUtil;
import com.newegg.ec.cache.app.util.HttpClientUtil;
import com.newegg.ec.cache.app.util.JedisUtil;
import com.newegg.ec.cache.core.logger.CommonLogger;
import com.newegg.ec.cache.plugin.INodeOperate;
import com.newegg.ec.cache.plugin.basemodel.Node;
import com.newegg.ec.cache.plugin.basemodel.PluginParent;
import com.newegg.ec.cache.plugin.basemodel.StartType;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
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
public class DockerManager extends PluginParent implements INodeOperate {

    private static CommonLogger logger = new CommonLogger( DockerManager.class );

    static ExecutorService executorService = Executors.newFixedThreadPool(100);

    @Value("${cache.docker.api.format}")
    private String dockerApiFormat;

    @Value("${cache.docker.image}")
    private String images;

    public DockerManager(){
        //ignore
    }

    @Autowired
    IDockerNodeDao dockerNodeDao;
    @Resource
    CheckLogic checkLogic;

    @Override
    public boolean pullImage(JSONObject pullParam) {

        boolean res = true;
        String ipStr = pullParam.getString( PluginParent.IPLIST_NAME );
        Set<String> ipSet = JedisUtil.getIPList(ipStr);
        String imageUrl = pullParam.getString( PluginParent.IMAGE );
        logger.websocket("start pull image ");
        List<Future<String>> futureList = new ArrayList<>();
        for(String ip : ipSet){
            Future<String> future = executorService.submit( new PullImageTask(ip, imageUrl) );
            futureList.add( future );
        }
        for(Future<String> future : futureList){
            try {
                String pullRes = future.get();
                logger.websocket( "pull image : " + pullRes );
            } catch (Exception e) {
                res = false;
                logger.websocket( e.getMessage() );
                logger.error("",e);
            }
        }
        logger.websocket("pull image is finish");
        return res;
    }

    @Override
    public boolean install(JSONObject installParam) {
        return installTemplate(this, installParam);
    }

    @Override
    protected boolean checkInstall(JSONObject reqParam) {
        Response checkRes =  checkLogic.checkDockerBatchInstall( reqParam );
        if( checkRes.getCode() == Response.DEFAULT ){
            return true;
        }
        return false;
    }

    @Override
    protected void installNodeList(JSONObject reqParam, List<RedisNode> nodelist) {
        List<Future<Boolean>> futureList = new ArrayList<>();
        nodelist.forEach(node -> {
            String ip = String.valueOf(node.getIp());
            String port = String.valueOf(node.getPort());
            String image = reqParam.getString("image");
            String name = reqParam.getString("container_name");
            String command = "/redis/redis-4.0.1/start.sh "+port+" "+ip;
            JSONObject installObject = generateInstallObject(image,name,command);
            futureList.add(executorService.submit(new RedisInstallTask(ip,installObject)));
        });
        for(Future<Boolean> future : futureList){
            try {
                future.get();
            } catch (Exception e) {
                logger.error("",e);
            }
        }
        logger.websocket("redis cluster node install success");
    }

    @Override
    protected void addNodeList(JSONObject reqParam, int clusterId) {

        String ipListStr = reqParam.getString(IPLIST_NAME);
        String image = reqParam.getString(  PluginParent.IMAGE );
        String containerName = reqParam.getString("containerName");
        List<RedisNode> nodelist = JedisUtil.getInstallNodeList(ipListStr);
        for(RedisNode redisNode : nodelist){
            DockerNode node = new DockerNode();
            node.setClusterId(clusterId);
            node.setContainerName( containerName + redisNode.getPort());
            node.setUserGroup(reqParam.get("userGroup").toString());
            node.setImage(image);
            node.setIp(node.getIp());
            node.setPort(node.getPort());
            node.setAddTime(DateUtil.getTime());
            dockerNodeDao.addDockerNode(node);
        }

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
        logger.websocket(removePram.toString());
        String ip = removePram.getString("ip");
        String containerId = removePram.getString("containerId");
        return deleteContainer(ip, containerId);
    }


    @Override
    public List<String> getImageList() {
        return Lists.newArrayList(images.split(","));
    }

    @Override
    public List<Node> getNodeList(int clusterId) {
        return dockerNodeDao.getDockerNodeList(clusterId);
    }

    /**
     * 获取container信息
     * 用于判断当前名称的容器是否存在
     * @param ip
     * @param containerId
     * @return
     */
    public JSONObject getContainerInfo(String ip, String containerId) {
        String res = null;
        try {
            String url = getContainerApi(ip);
            res = HttpClientUtil.getGetResponse(url, containerId + "/json");
        } catch (Exception e) {
            logger.error( "", e );
        }
        JSONObject result = JSONObject.fromObject(res);
        return result;
    }

    /**
     * create  container 创建失败会返回一个空的json串
     * @param ip
     * @param param
     * @return
     */
    public JSONObject createContainer(String ip,JSONObject param){

        //返回的结果
        JSONObject result = new JSONObject();
        //获取创建容器所需要的参数

        String containerName = param.getString("HostName");
        try {
            //create 容器（镜像会先拉去到指定的机器上）
            result = JSONObject.fromObject(HttpClientUtil.getPostResponse(getContainerApi(ip)+"/create?name="+containerName, param));
            String container_id = result.getString("Id");
            //启动容器
            optionContainer(ip, container_id, StartType.start);
            result.put("result","true");
        } catch (IOException e) {
            result.put("result", false );
            logger.error("", e);
        }

        return result;
    }

    /**
     * container操作 : start stop restart
     * @param ip
     * @param containerName
     * @param startType
     * @return
     */
    public boolean optionContainer(String ip,String containerName, StartType startType) {
        try {
            HttpClientUtil.getPostResponse(getContainerApi(ip) + "/" + containerName + "/" + startType, new JSONObject());
        }catch (Exception e){
            logger.error("", e);
            return false;
        }
        return true;
    }

    /**
     * 删除容器
     * @param ip
     * @param containerName
     * @return
     */
    public boolean deleteContainer(String ip,String containerName) {
        optionContainer(ip,containerName, StartType.stop);
        try {
            HttpClientUtil.getDeleteResponse(getContainerApi(ip), containerName);
        }catch (Exception e){
            logger.error("", e);
            return false;
        }
        return true;
    }

    /**
     * pull image 操作
     * @param url
     * @param imageVersion
     * @return
     */
    public boolean imagePull(String url,String imageVersion) throws IOException {
        String imageName = images + imageVersion;
        String temp  = url+"/create?fromImage=" + imageName;
        HttpClientUtil.getPostResponse(temp, new JSONObject());
        return true;
    }

    /**
     * 构建生成容器所需要的参数
     * @param image
     * @param name
     * @param command
     * @return
     */
    private JSONObject generateInstallObject(String image, String name, String command){

        JSONObject req = new JSONObject();
        req.put("Image", image);
        req.put("HostName", name);
        JSONObject hostConfig = new JSONObject();
        hostConfig.put("NetworkMode", "host");
        JSONObject restartPolicy = new JSONObject();
        restartPolicy.put("Name", "always");
        hostConfig.put("RestartPolicy", restartPolicy);
        JSONArray binds = new JSONArray();
        String bindStr = "/data/redis:/data/redis" ;
        binds.add( bindStr );
        hostConfig.put("Binds", binds);
        req.put("HostConfig", hostConfig);
        String[] cmds = command.split("\\s+");
        req.put("Cmd", cmds);

        return req;
    }

    /**
     * pull images task
     */
    class PullImageTask implements Callable<String> {
        private String image;
        private String ip;
        public PullImageTask(String ip, String image){
            this.ip = ip;
            this.image = image;
        }

        @Override
        public String call() throws Exception {
            String res = "";
            try {
                String url = getImageApi(ip);
                logger.websocket("start pull image " + url);
                imagePull(url,image);
            }catch (Exception e){
                res = logger.websocket( e.getMessage() );
            }
            return res;
        }
    }

    class RedisInstallTask implements Callable<Boolean> {
        private String ip;
        private JSONObject installObj ;

        public RedisInstallTask(String ip,JSONObject installObj) {
            this.ip = ip;
            this.installObj = installObj;
        }

        @Override
        public Boolean call() throws Exception {
            if(createContainer(ip,installObj) != null){
                return true;
            }
            return false;
        }

    }

    private  String getContainerApi(String ip){
        return getDockerRestfullApi(ip) + "/containers";
    }

    private String getImageApi(String ip){
        return getDockerRestfullApi(ip) + "/images";
    }

    private String getDockerRestfullApi(String ip){
        return String.format(dockerApiFormat, ip);
    }


}
