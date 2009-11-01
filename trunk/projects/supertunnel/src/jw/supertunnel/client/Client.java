package jw.supertunnel.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import jw.supertunnel.Constants;
import jw.supertunnel.server.ResponseException;

/**
 * 
 * @author Alexander Boyd
 * 
 */
public class Client
{
    public static ServerSocket server;
    
    public static String configTargetHost;
    public static int configTargetPort;
    public static int configPort = 10229;
    
    public static void main(String[] args) throws Throwable
    {
        parseArgs(args);
        System.out.println("Listening on port " + configPort + " and tunneling to "
                + configTargetHost + " on port " + configTargetPort);
        server = new ServerSocket(configPort);
        while (true)
        {
            Socket socket = server.accept();
            try
            {
                Connection connection = new Connection(socket);
                connection.start();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                try
                {
                    socket.close();
                }
                catch (Exception ex2)
                {
                    ex2.printStackTrace();
                }
            }
        }
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
    
    public static URL createRequestUrl(String query)
    {
        try
        {
            return new URI("http", null, configTargetHost, configTargetPort, "/index.html",
                    query, null).toURL();
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException(e);
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    public static Response request(String method, String query, byte[] input)
            throws IOException
    {
        if (query == null)
            query = "bogus=" + generateBogus();
        else
            query = "bogus=" + generateBogus() + "&" + query;
        URL url = createRequestUrl(query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setDoOutput(input != null);
        if (input != null)
        {
            OutputStream out = connection.getOutputStream();
            out.write(input);
            out.flush();
            out.close();
        }
        Response response = new Response();
        response.status = connection.getResponseCode();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream in = connection.getInputStream();
        copy(in, baos, 65000);
        in.close();
        baos.close();
        response.data = baos.toByteArray();
        if (connection.getHeaderField("Send-Data-Length") != null)
        {
            int length = Integer.parseInt(connection.getHeaderField("Send-Data-Length"));
            if (response.data.length != length)
            {
                throw new IOException("Mismatched response length, expected " + length
                        + " but received " + response.data.length);
            }
        }
        return response;
    }
    
    private static String generateBogus()
    {
        return "" + System.currentTimeMillis() + "-" + Math.random();
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
                throw new ResponseException(Constants.httpTooMuchData);
        }
    }
    
}
