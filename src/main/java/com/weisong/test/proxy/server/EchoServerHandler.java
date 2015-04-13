package com.weisong.test.proxy.server;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.weisong.test.message.EchoRequest;
import com.weisong.test.message.EchoResponse;
import com.weisong.test.util.ProxyUtil;

@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            EchoServerHandler.class.getName());

    public EchoServerHandler() {
    	System.out.println("Created " + getClass().getSimpleName());
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    	if(msg instanceof EchoRequest) {
        	EchoRequest request = (EchoRequest) msg;
        	EchoResponse response = new EchoResponse(request.getId(), "Pong");
        	response.setUserData(request.getUserData());
            ProxyUtil.sendMessage(ctx.channel(), response);
    	}
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.log(Level.WARNING, "Unexpected exception from downstream.", cause);
        ctx.close();
    }
}
