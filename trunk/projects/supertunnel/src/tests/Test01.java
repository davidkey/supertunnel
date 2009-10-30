package tests;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Test01
{
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Throwable
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(12556), 50);
        server.createContext("/some/path", new HttpHandler()
        {
            
            @Override
            public void handle(HttpExchange exchange) throws IOException
            {
                String requestMethod = exchange.getRequestMethod();
                if (requestMethod.equalsIgnoreCase("GET"))
                {
                    Headers responseHeaders = exchange.getResponseHeaders();
                    responseHeaders.set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(200, 0);
                    
                    OutputStream responseBody = exchange.getResponseBody();
                    Headers requestHeaders = exchange.getRequestHeaders();
                    Set<String> keySet = requestHeaders.keySet();
                    Iterator<String> iter = keySet.iterator();
                    while (iter.hasNext())
                    {
                        String key = iter.next();
                        List values = requestHeaders.get(key);
                        String s = key + " = " + values.toString() + "\n";
                        responseBody.write(s.getBytes());
                    }
                    responseBody.close();
                }
            }
        });
        server.start();
    }
    
}
