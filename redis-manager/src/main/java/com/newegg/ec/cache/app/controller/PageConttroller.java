package com.newegg.ec.cache.app.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author Jay.H.Zou
 * @date 2018/4/28
 */

@Controller
public class PageConttroller {

    @RequestMapping("/")
    public String accessIndex(){
        return "index";
    }

    @RequestMapping("/pages/redisMonitorList")
    public String accessMonitorList(){
        return "redisMonitorList";
    }

    @RequestMapping("/pages/clusterListManager")
    public String accessClusterListManager(){
        return "clusterListManager";
    }

    @RequestMapping("/pages/createCluster")
    public String accessCreateCluster(){
        return "createCluster";
    }

    @RequestMapping("/pages/managerCluster")
    public String accessManagerCluster(){
        return "managerCluster";
    }

    @RequestMapping("/pages/redisMonitorDetail")
    public String accessClusterDetail(){
        return "redisMonitorDetail";
    }
}
