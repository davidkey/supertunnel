package tests;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

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
                System.out.println(exchange.getRequestURI().getRawQuery());
                System.out.println(exchange.getRequestURI().toString());
                exchange.getResponseHeaders().add("Content-type", "text/html");
                exchange.sendResponseHeaders(200, 0);
                OutputStream out = exchange.getResponseBody();
                out.write("<html><body>Hello world<br/>Bye\n</body></html>\n".getBytes());
                out.flush();
                out.close();
                exchange.close();
            }
        });
        server.start();
    }
    
}
