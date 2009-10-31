package jw.supertunnel.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Server
{
    public static HttpServer httpServer;
    
    public static int configPort = 80;
    
    public static int configReceiveBufferSize = 512;
    public static int configReceiveQueueSize = 4000;
    public static int configMaxChunkSize = 50000;
    public static int configIdleTimeout = 1000 * 60 * 2;
    public static int configMaxConnections = 10;
    public static String configTargetHost;
    public static int configTargetPort;
    
    public static final Object lock = new Object();
    
    public static ThreadPoolExecutor httpExecutor = new ThreadPoolExecutor(20, 100, 67,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(800));
    
    public static Map<String, Connection> connectionMap = Collections
            .synchronizedMap(new HashMap<String, Connection>());
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Throwable
    {
        parseArgs(args);
        setupHttpServer();
    }
    
    private static void parseArgs(String[] argsArray)
    {
        Queue<String> args = new ArrayDeque<String>(Arrays.asList(argsArray));
        boolean gotHost = false;
        while (args.size() > 0)
        {
            String next = args.poll();
            if (!next.startsWith("-"))
            {
                gotHost = true;
                String[] split = next.split("\\:");
                configTargetHost = split[0];
                configTargetPort = Integer.parseInt(split[1]);
                continue;
            }
            if (next.equals("-p"))
            {
                configPort = Integer.parseInt(args.poll());
            }
            else
            {
                throw new RuntimeException("Unrecognized argument \"" + next
                        + "\", try adding --help onto the end of the command.");
            }
        }
        if (!gotHost)
            throw new RuntimeException("You didn't specify a host/port to connect to, "
                    + "in the format \"host:port\" at the end of the command.");
    }
    
    private static void setupHttpServer() throws IOException
    {
        httpExecutor.allowCoreThreadTimeOut(true);
        httpServer = HttpServer.create(new InetSocketAddress(configPort), 100);
        httpServer.setExecutor(httpExecutor);
        httpServer.createContext("/", new HttpHandler()
        {
            
            @Override
            public void handle(HttpExchange exchange) throws IOException
            {
                String query = exchange.getRequestURI().getRawQuery();
                HashMap<String, String> parameters = new HashMap<String, String>();
                if (query != null && !query.equals(""))
                {
                    String[] paramList = query.split("\\&");
                    for (String param : paramList)
                    {
                        String[] split = param.split("\\=", 2);
                        if (split.length == 1)
                            continue;
                        parameters.put(URLDecoder.decode(split[0]), URLDecoder
                                .decode(split[1]));
                    }
                }
                processHttpRequest(exchange, parameters);
            }
        });
        httpServer.start();
    }
    
    protected static void processHttpRequest(HttpExchange exchange,
            HashMap<String, String> parameters) throws IOException
    {
        String action = parameters.get("action");
        if (action == null)
            throw new IOException("The action parameter must be specified.");
        if (action.equals("create"))
            doCreateRequest(exchange, parameters);
        else if (action.equals("destroy"))
            doDestroyRequest(exchange, parameters);
        else if (action.equals("send"))
            doSendRequest(exchange, parameters);
        else if (action.equals("receive"))
            doReceiveRequest(exchange, parameters);
        else if (action.equals("ping"))
            doPingRequest(exchange, parameters);
        else
            throw new IOException("Invalid action, needs to be one of create, "
                    + "destroy, send, receive, or ping.");
    }
    
    private static void doCreateRequest(HttpExchange exchange,
            HashMap<String, String> parameters) throws IOException
    {
        Connection connection = null;
        synchronized (lock)
        {
            if (connectionMap.size() > configMaxConnections)
                throw new IOException("Too many active connections");
            /*
             * We're good to set up the connection. We'll create a connection object and
             * add it to the connection map. Then we'll exit the synchronized block. Once
             * we've exited, we'll establish an actual socket connection and set up the
             * threads for the connection, while synchronized on the connection itself.
             */
            connection = new Connection();
            connection.connectionId = generateConnectionId();
            connectionMap.put(connection.connectionId, connection);
        }
        synchronized (connection)
        {
            connection.setup();
        }
        exchange.getResponseHeaders().add("Number-of-connections",
                "" + connectionMap.size());
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        out.write(connection.connectionId.getBytes());
        out.flush();
        out.close();
        exchange.close();
    }
    
    private static void doDestroyRequest(HttpExchange exchange,
            HashMap<String, String> parameters) throws IOException
    {
        Connection connection = connectionMap.get(parameters.get("connection"));
        if (connection == null)
            throw new IOException("No such connection");
        connectionMap.remove(connection.connectionId);
        connection.socket.close();
        exchange.sendResponseHeaders(200, 0);
        exchange.close();
    }
    
    private static void doSendRequest(HttpExchange exchange,
            HashMap<String, String> parameters) throws IOException
    {
        byte[] data = readData(exchange);
        Connection connection = connectionMap.get(parameters.get("connection"));
        if (connection == null)
            throw new IOException("No such connection");
        connection.lastWriteTime = System.currentTimeMillis();
        synchronized (connection)
        {
            // sequence,length
            long requestedSequence = Long.parseLong(parameters.get("sequence"));
            int length = Integer.parseInt(parameters.get("length"));
            if (!(data.length == length))
                throw new IOException("Data/length mismatch, specified " + length
                        + " but sent " + data.length);
            if (requestedSequence > connection.lastReadSequence)
            {
                connection.lastReadSequence = requestedSequence;
                connection.output.write(data);
            }
        }
        exchange.sendResponseHeaders(200, 0);
        exchange.close();
    }
    
    public static final byte[] endOfStream = new byte[0];
    
    private static void doReceiveRequest(HttpExchange exchange,
            HashMap<String, String> parameters) throws IOException
    {
        Connection connection = connectionMap.get(parameters.get("connection"));
        if (connection == null)
            throw new IOException("No such connection");
        connection.lastWriteTime = System.currentTimeMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try
        {
            boolean streamEnded = false;
            byte[] bytes = connection.receiveQueue.poll(30, TimeUnit.SECONDS);
            while (bytes != null)
            {
                out.write(bytes);
                if (bytes == endOfStream)
                {
                    streamEnded = true;
                    break;
                }
                bytes = connection.receiveQueue.poll();
            }
            if (streamEnded)
            {
                connectionMap.remove(connection.connectionId);
            }
        }
        catch (InterruptedException e)
        {
            throw new IOException("STUPID, GOSHDARNED INTERRUPTEDEXCEPTION", e);
        }
        byte[] bytes = out.toByteArray();
        exchange.getResponseHeaders().add("Send-data-length", "" + bytes.length);
        exchange.sendResponseHeaders(200, 0);
        OutputStream output = exchange.getResponseBody();
        output.write(bytes);
        output.flush();
        output.close();
        exchange.close();
    }
    
    private static void doPingRequest(HttpExchange exchange,
            HashMap<String, String> parameters) throws IOException
    {
        Connection connection = connectionMap.get(parameters.get("connection"));
        if (connection == null)
            throw new IOException("No such connection");
        connection.lastWriteTime = System.currentTimeMillis();
    }
    
    public static byte[] readData(HttpExchange exchange) throws IOException
    {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out, configMaxChunkSize);
        in.close();
        out.close();
        return out.toByteArray();
    }
    
    public static void copy(InputStream in, OutputStream out) throws IOException
    {
        copy(in, out, -1);
    }
    
    public static void copy(InputStream in, OutputStream out, int max) throws IOException
    {
        byte[] buffer = new byte[256];
        int amount;
        int total = 0;
        while ((amount = in.read(buffer)) != -1)
        {
            out.write(buffer, 0, amount);
            total += amount;
            if (max > 0 && total > max)
                throw new IOException("Too much data to be read, max is " + max
                        + " and total is " + total);
        }
    }
    
    public static String generateConnectionId()
    {
        return "" + System.currentTimeMillis()
                + ("" + Math.random()).replaceAll("[^0-9]", "");
    }
}
