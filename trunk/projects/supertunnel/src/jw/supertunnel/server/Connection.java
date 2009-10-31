package jw.supertunnel.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Connection
{
    /**
     * A thread that checks every 30 seconds to see when data was last. If it's been more
     * than two minutes, the socket is closed. This results in the read thread removing
     * this connection from the connection map. This makes it so that if a client gets
     * disconnected and forgets to destroy the connection (or is unable to due to network
     * failure), the connection will still eventually be closed.
     * 
     * @author Alexander Boyd
     * 
     */
    public class TimeoutThread extends Thread
    {
        public void run()
        {
            while (Server.connectionMap.get(connectionId) == Connection.this)
            {
                try
                {
                    Thread.sleep(30 * 1000);
                    if (isPastTimeout())
                    {
                        System.out.println("timing out");
                        try
                        {
                            socket.close();
                        }
                        catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                        Server.connectionMap.remove(connectionId);
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        
        public boolean isPastTimeout()
        {
            long intoThePast = System.currentTimeMillis() - Server.configIdleTimeout;
            if (intoThePast > lastWriteTime)
                return true;
            return false;
        }
    }
    
    public class ReadThread extends Thread
    {
        public void run()
        {
            try
            {
                int amount;
                while ((amount = input.read(receiveBuffer)) != -1)
                {
                    syncBufferToQueue(amount);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    receiveQueue.offer(Server.endOfStream);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
                try
                {
                    socket.close();
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    }
    
    public byte[] receiveBuffer = new byte[Server.configReceiveBufferSize];
    public BlockingQueue<byte[]> receiveQueue = new LinkedBlockingQueue<byte[]>();
    public Socket socket;
    public InputStream input;
    public OutputStream output;
    public long lastReadSequence;
    public String connectionId;
    public ReadThread readThread;
    public TimeoutThread timeoutThread;
    public long lastWriteTime;
    
    public void setup()
    {
        /*
         * In this method, we start the read thread and the timeout thread.
         */
        readThread = new ReadThread();
        timeoutThread = new TimeoutThread();
        try
        {
            socket = new Socket(Server.configTargetHost, Server.configTargetPort);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            lastWriteTime = System.currentTimeMillis();
            readThread.start();
            timeoutThread.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            try
            {
                socket.close();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
            Server.connectionMap.remove(connectionId);
        }
    }
    
    public void syncBufferToQueue(int amount)
    {
        byte[] bytes = Arrays.copyOf(receiveBuffer, amount);
        while (true)
        {
            if (Server.connectionMap.get(connectionId) != this)
                throw new RuntimeException("syncBufferToQueue failing on closed connection");
            try
            {
                if (receiveQueue.offer(bytes, 30, TimeUnit.SECONDS))
                    return;
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
}
