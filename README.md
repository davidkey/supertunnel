# supertunnel

Forked from https://code.google.com/p/supertunnel/.

License: GNU LGPL (https://www.gnu.org/licenses/lgpl.html)

# What's the deal with the fork?

Updated to compile / build with maven. Also creates two runnable jars - client and server.

To build:
```sh
mvn package
```

#Original description from google code

SuperTunnel is a program that can tunnel normal TCP traffic over HTTP. This can be used to get around firewalls that block all traffic except HTTP.

In layman's terms, if you're at a place like school where their computers don't let you visit certain websites like facebook.com, you can almost always use SuperTunnel to get around it and visit those sites anyway.

Now back to geek's terms. The way it works is this: you start a SuperTunnel server on a computer you own that's outside of the firewall you want to get around. When you start the server, you tell it what address and port it will connect to, and what port it will listen on. Then, on a computer behind the firewall, you start the SuperTunnel client, and tell it a port it should listen on, and the server and port that you're running your SuperTunnel server on. When an application on your firewalled computer connects to the SuperTunnel client, it makes a set of HTTP requests to the SuperTunnel server, which establishes a connection to the address and port you specified when starting the server.

So, for example, you could use SuperTunnel to forward SSH connections to a server you own. You could then start the SSH client with the -D option (which tells it to start a SOCKS proxy that sends all traffic to the server), set your browser to use that proxy, and when you visit web pages and such, they will be forwarded past the firewall by SuperTunnel. Since SSH encrypts traffic, the firewall can't see anything going on besides a program (SuperTunnel) making ordinary HTTP requests to a server.

SuperTunnel's code is currently stored at the SVN repository at http://jwutils.googlecode.com. I'll post a download here shortly, but for now, you'll need to do an svn checkout on http://jwutils.googlecode.com/svn/trunk/projects/supertunnel, cd into the folder you checked out, and run "ant compile" (which requires you have Ant and Java installed; these can be installed via apt-get as "ant" and "sun-java6-jdk" respectively) to build SuperTunnel. I'll get directions on how to actually run it up soon; if you have questions in the mean time, send an email to the project owners listed at right (specifically "javawiz...").

If you've seen GNU HttpTunnel (and I have), you might be wondering why I wrote SuperTunnel. The main reason is that HttpTunnel keeps a single HTTP connection to the HttpTunnel server open even after some data has been sent across it. The firewall I wrote SuperTunnel to get around cached requests until a good deal of data had been sent across them, which caused problems with HttpTunnel. SuperTunnel does not do this: requests made when no data is yet available will block, but once a single byte is sent across, the server ends the request. The client puts in another request a second later, and the server sends any additional data it's accumulated to the client and then terminates that request.

HTTP connections to a SuperTunnel server live no longer than 30 seconds. If no data has arrived by then, the server terminates the request, and the client puts in another one a second later.

Each HTTP request can carry up to 50KB of data. Because SuperTunnel makes one request per second, this effectively limits bandwidth over a SuperTunnel connection to 50KB/s. Without modification, SuperTunnel generally isn't good for transferring large amounts of data. If you have a server, though, that has plenty of memory (say, 200MB free), you could up this limit to 10MB per request if you wanted. (The memory requirement is because SuperTunnel buffers unsent data in memory.)

Also, SuperTunnel is written in Java, so you'll need a Java VM to run it.

# more info


SuperTunnel is an application for tunneling normal TCP connections over HTTP. It is similar to GNU's HttpTunnel, but its two main differences are that it does not keep HTTP connections with half of the data sent open (which results in it running somewhat slower but being able to operate over a wider range of firewalls, specifically the firewall at my dad's work) and it allows multiple virtual connections at the same time.

SuperTunnel is written in Java, so no special modifications are needed to get it to run across multiple platforms. This means that both the client and the server work on Windows, Linux, Mac, BSD, and others.

----------------------------------------------------

Any URL sent to the server will work correctly, IE the path doesn't matter, which will allow, in the future, for multiple SuperTunnel servers running on the same actual server by putting a reverse proxy in front of them and having it redirect certain URLs to certain servers. The client could then be configured to connect with a specific URL.

For any given URL, there is a query parameter called bogus, which contains random data. This is used to stop firewalls from caching stuff. Right now, it will be the toString version of Math.random, concatenated twice.

There is a query parameter called action, which is one of create, send, receive, or destroy. 

Create means that we want to establish a new virtual connection. There are no additional query parameters. The response body contains an id for the connection, which will consist only of numbers.

Destroy means that we want to destroy an existing virtual connection. This closes the connection on the remote side.

Send means that we have some data to send to the socket. There is a query parameter called connection, which is the id of the connection as received by create. The request data is the data to send. This data can be empty, which results in data not being sent. This is useful since SuperTunnel will automatically close a virtual connection after 120 seconds if no data is sent to it. There is also a query parameter called sequence. This is incremented every time some data is sent. The server keeps track of the last received sequence from the client, and ignores any data with a sequence number less than or equal to the last received sequence. There is also a query parameter called length, which is the length of the data that's being sent. The server will close the connection if the length is not correct.

Receive means we want to read some data from the socket. There is a query parameter called connection which is the id of the connection to receive some data from. The server will block for up to 30 seconds waiting for data to become available. When the server responds, the response is the received data. It also specifies a header, Send-data-length, which is the length of data that was sent.

For destroy, send, and receive, if there is no such connection, a 500 status code is returned.

The client has a concept of a send queue and a send buffer. The server has a concept of a receive queue and a receive buffer. The send queue is a BlockingQueue of byte arrays. The queue's maximum size is, by default, 400. The send buffer is an array of bytes that is used to read data from the client-side socket. Whenever data is read into the send buffer, it is then copied onto the send queue. The send buffer's default size is 500.

The receive buffer and receive queue are almost identical. The receive queue has a maximum size of, by default, 2000. The receive buffer's default size is 500.

When a receive request is made to the server, the server pulls byte arrays off of the receive queue until it has either reached 50KB in size or the queue is empty. It then sends this data to the client. The first data pull operation blocks for up to 30 seconds. The rest do not block at all.

When new data arrives on the socket on the client side, it adds it to the send queue.

A thread runs on the client. This thread pulls byte arrays off of the send queue. Whenever a byte array is available, the thread tries to pull as many byte array as it can until it has reached 50KB in size. It then sends this off to the server. Then, it waits one half of a second.

Another thread runs on the client. This thread makes a receive request to the server. When the server responds, the thread writes the data to the socket. It then waits one half of a second, and attempts to receive data again. 

When a connection is established, the server will only allow future requests relating to that connection to come from the same ip address that the connection was created from. This makes it so that an attacker can't shut other people's connections down.




