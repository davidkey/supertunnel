package jw.supertunnel.client;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

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
            Connection connection = new Connection(socket);
            connection.start();
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
    
}
