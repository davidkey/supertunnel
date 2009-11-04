package jw.supertunnel.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jw.supertunnel.server.Server;

/**
 * When we get a socket connection we start a new connection object. This starts a read
 * thread, a receive thread, and a send thread.
 * 
 * @author Alexander Boyd
 */
public class Connection
{
    /**
     * The read thread continuously reads from the socket and sticks the bytes it reads
     * into the send buffer. It then sticks this as a packet onto the send queue, blocking
     * if there isn't space available. It terminates when the socket gets closed.
     * 
     * @author Alexander Boyd
     * 
     */
    public class ReadThread extends Thread
    {
        public void run()
        {
            try
            {
                int amount;
                while ((amount = input.read(sendBuffer)) != -1)
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
                destroyConnection();
            }
        }
    }
    
    public final Object destroyLock = new Object();
    
    /**
     * The receive thread continuously puts in read requests to the server. When it
     * receives data, if the data's length is greater than 0, it writes the data out to
     * the socket. If this thread gets a response that there is no such connection, it
     * closes the socket.
     * 
     * @author Alexander Boyd
     * 
     */
    public class ReceiveThread extends Thread
    {
        public void run()
        {
            try
            {
                while (true)
                {
                    System.out.println("starting receive");
                    Response response = Client.request("GET", "action=receive&connection="
                            + connectionId, null);
                    System.out.println("finished receive");
                    output.write(response.data);
                    output.flush();
                    Thread.sleep(500);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                destroyConnection();
                try
                {
                    socket.close();
                }
                catch (Exception e2)
                {
                    e2.printStackTrace();
                }
            }
        }
    }
    
    /**
     * The send thread continuously pulls packets off of the send queue, bunches them up
     * together into a packet up to 50k in size, and sends them off to the server, making
     * sure to wait for the correct response before moving on to the next bunch of
     * packets. It sleeps one half of a second between each request. If this thread
     * detects that the source socket is closed, and there are no items in the queue, it
     * sends off a connection destroy packet and terminates.
     * 
     * @author Alexander Boyd
     * 
     */
    public class SendThread extends Thread
    {
        public void run()
        {
            try
            {
                while (socket != null && !socket.isClosed())
                {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] bytes = sendQueue.poll(30, TimeUnit.SECONDS);
                    while (bytes != null)
                    {
                        out.write(bytes);
                        bytes = sendQueue.poll();
                    }
                    sendToServer(out.toByteArray());
                    Thread.sleep(500);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                destroyConnection();
                try
                {
                    socket.close();
                }
                catch (Exception e2)
                {
                    e2.printStackTrace();
                }
            }
        }
    }
    
    public Connection(Socket socket)
    {
        this.socket = socket;
    }
    
    public void sendToServer(byte[] data) throws IOException
    {
        System.out.println("starting send");
        Response response = Client.request("POST", "action=send&connection=" + connectionId
                + "&length=" + data.length + "&sequence=" + (lastReadSequence++), data);
        System.out.println("finished send");
        if (response.status != 200)
            throw new IOException("Response code " + response.status
                    + " received, expected 200");
    }
    
    public void destroyConnection()
    {
        synchronized (destroyLock)
        {
            try
            {
                Response response = Client.request("GET", "action=destroy&connection="
                        + connectionId, null);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    
    public void start() throws IOException
    {
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
        try
        {
            establishConnection();
        }
        catch (Exception e)
        {
            try
            {
                socket.close();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
            System.out.println("Could not create a connection on the target");
            e.printStackTrace(System.out);
            return;
        }
        readThread.start();
        receiveThread.start();
        sendThread.start();
    }
    
    private void establishConnection() throws IOException
    {
        Response response = Client.request("GET", "action=create", null);
        if (response.status != 200)
            throw new IOException("Response code " + response.status
                    + " received from the server");
        connectionId = new String(response.data).trim();
    }
    
    public byte[] sendBuffer = new byte[1024];
    public BlockingQueue<byte[]> sendQueue = new LinkedBlockingQueue<byte[]>(1000);
    public Socket socket;
    public InputStream input;
    public OutputStream output;
    public long lastReadSequence = 1;
    public String connectionId;
    public ReadThread readThread = new ReadThread();
    public ReceiveThread receiveThread = new ReceiveThread();
    public SendThread sendThread = new SendThread();
    
    public void syncBufferToQueue(int amount)
    {
        byte[] bytes = Arrays.copyOf(sendBuffer, amount);
        boolean socketClosed1 = false;
        while (true)
        {
            try
            {
                if (sendQueue.offer(bytes, 30, TimeUnit.SECONDS))
                    return;
                if (socket.isClosed() && socketClosed1)
                    return;
                else if (socket.isClosed())
                    socketClosed1 = true;
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
}
