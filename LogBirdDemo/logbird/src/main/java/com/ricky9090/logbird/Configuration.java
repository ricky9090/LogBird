package com.ricky9090.logbird;

public class Configuration {

    public static Configuration getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final Configuration INSTANCE = new Configuration();
    }

    // 指定Tag，用于Log查看，与浏览器页面参数一致
    private String logTag;

    // 服务端上传路径
    // http://IP:PORT/uploadlog
    private String logBirdServer;

    public String getLogTag() {
        return logTag;
    }

    public void setLogTag(String logTag) {
        this.logTag = logTag;
    }

    public String getLogBirdServer() {
        return logBirdServer;
    }

    public void setLogBirdServer(String logBirdServer) {
        this.logBirdServer = logBirdServer;
    }
}
