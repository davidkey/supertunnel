# supertunnel

Forked from https://code.google.com/p/supertunnel/.

License: GNU LGPL (https://www.gnu.org/licenses/lgpl.html)

# What's the deal with the work?

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

Feel free to contact me using the email at right if you have questions or need help setting up SuperTunnel.
