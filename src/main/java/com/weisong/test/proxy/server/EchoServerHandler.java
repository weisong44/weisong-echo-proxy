package com.weisong.test.proxy.server;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.weisong.test.message.EchoRequest;
import com.weisong.test.message.EchoResponse;
import com.weisong.test.util.ProxyUtil;

@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

	private AtomicInteger count = new AtomicInteger();
	private long timeoutInterval = 100; // 1 timeout for every n good requests
	
    private static final Logger logger = Logger.getLogger(
            EchoServerHandler.class.getName());

    public EchoServerHandler() {
    	System.out.println("Created " + getClass().getSimpleName());
    }
    
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
    	if(msg instanceof EchoRequest) {
        	final EchoRequest request = (EchoRequest) msg;
    		if(timeoutInterval > 0 && count.incrementAndGet() % timeoutInterval == 0) {
    			new Thread() {
    				public void run() {
    					try {
    						System.out.println("Delay request for 110 ms, counter=" + count.get());
							sleep(110);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
    	    			process(ctx, request);
    				}
    			}.start();
    		}
    		else {
    			process(ctx, request);
    		}
    	}
    }
    
    private void process(ChannelHandlerContext ctx, EchoRequest request) {
    	EchoResponse response = new EchoResponse(request.getId(), "Pong");
    	response.setUserData(request.getUserData());
        ProxyUtil.sendMessage(ctx.channel(), response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.log(Level.WARNING, "Unexpected exception from downstream.", cause);
        ctx.close();
    }
}
