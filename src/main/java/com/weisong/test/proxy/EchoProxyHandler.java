package com.weisong.test.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.weisong.test.message.EchoRequest;
import com.weisong.test.message.EchoResponse;

public class EchoProxyHandler {
	static public class ClientSide extends ChannelInboundHandlerAdapter {

		final private Logger logger = Logger.getLogger(getClass().getName());
	    
	    public ClientSide(EchoProxyEngine engine) {
	    	this.engine = engine;
	    }
	    
	    private EchoProxyEngine engine;

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
	        ctx.close();
	        engine.disconnectedFromClient(ctx.channel());
		}

		@Override
	    public void channelRead(ChannelHandlerContext ctx, Object msg) {
			if(msg instanceof EchoRequest) {
				engine.receivedMessageFromClient(ctx.channel(), (EchoRequest) msg);
			}
	    }

	    @Override
	    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
	        logger.log(Level.WARNING, String.format(
	    		"Exception from upstream: %s, %s", 
	    		ctx.channel().remoteAddress(), cause.getMessage()));
	        ctx.close();
	        engine.disconnectedFromClient(ctx.channel());
	    }
	}
	
	static public class ServerSide extends ChannelInboundHandlerAdapter {

		final private Logger logger = Logger.getLogger(getClass().getName());
	    
	    public ServerSide(EchoProxyEngine engine) {
	    	this.engine = engine;
	    }
	    
	    private EchoProxyEngine engine;

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
	        ctx.close();
	        engine.disconnectedFromServer(ctx.channel());
		}

		@Override
	    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if(msg instanceof EchoResponse) {
				engine.receivedMessageFromServer(ctx.channel(), (EchoResponse) msg);
			}
	    }

	    @Override
	    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
	        logger.log(Level.WARNING, String.format(
	    		"Exception from downstream: %s, %s", 
	    		ctx.channel().remoteAddress(), cause.getMessage()));
	        ctx.close();
	        engine.disconnectedFromServer(ctx.channel());
	    }
	}
}
