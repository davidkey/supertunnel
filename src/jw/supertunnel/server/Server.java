package jw.supertunnel.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

public class Server
{
    public static HttpServer httpServer;
    
    public static int configPort = 80;
    
    public static ThreadPoolExecutor httpExecutor = new ThreadPoolExecutor(20, 100, 67,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(800));
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Throwable
    {
        setupHttpServer();
    }
    
    private static void setupHttpServer() throws IOException
    {
        httpExecutor.allowCoreThreadTimeOut(true);
        httpServer = HttpServer.create(new InetSocketAddress(configPort), 100);
        httpServer.setExecutor(httpExecutor);
    }
}
