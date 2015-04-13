package com.weisong.test.proxy.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import com.weisong.test.codec.EchoMessageCodec;

/**
 * Sends one message when a connection is open and echoes back any received
 * data to the server.  Simply put, the echo client initiates the ping-pong
 * traffic between the echo client and server by sending the first message to
 * the server.
 */
public class EchoClient {

	static public int numberOfRequests = 10000;
	
    private final String host;
    private final int port;

    public EchoClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void run() throws Exception {
        // Configure the client.
        EventLoopGroup group = new NioEventLoopGroup();
        try {
			Bootstrap b = new Bootstrap();
			b.group(group)
				.channel(NioSocketChannel.class)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(
							// new LoggingHandler(LogLevel.DEBUG),
							new EchoMessageCodec(),
							new EchoClientHandler());
					}
				});

			// Start the client.
			ChannelFuture[] array = new ChannelFuture[5];
			for(int i = 0; i < array.length; i++) {
				array[i] = b.connect(host, port).sync();
			}

			// Wait until the connection is closed.
			array[0].channel().closeFuture().sync();
			
        } finally {
            // Shut down the event loop to terminate all threads.
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        if (args.length != 3) {
            System.err.println("Usage:");
            System.err.println("    java " + EchoClient.class.getSimpleName() + 
            		" <host> <port> <number-of-requests>");
            return;
        }

        // Parse options.
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        
        EchoClient.numberOfRequests = Integer.valueOf(args[2]);

        new EchoClient(host, port).run();
        
    }
}
