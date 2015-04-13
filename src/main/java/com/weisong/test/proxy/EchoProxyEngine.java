package com.weisong.test.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.weisong.test.codec.EchoMessageCodec;
import com.weisong.test.message.EchoRequest;
import com.weisong.test.message.EchoResponse;
import com.weisong.test.util.ProxyUtil;

public class EchoProxyEngine {

	private boolean shutdown;
	
	private Timer timer = new Timer();
	// Server connection string and connections
	private String[] serverConnStrings;
	// Server connection map
	private ArrayList<Channel> serverConnections = new ArrayList<>();
	// Request map
	private Map<String, EchoRequestContext> requestContextMap = new ConcurrentHashMap<>();

	private Bootstrap serverBootstrap;
    private EventLoopGroup eventLoop = new NioEventLoopGroup();
    
	final private Logger logger = Logger.getLogger(getClass().getName());
    
	public EchoProxyEngine(String[] serverConnStrings) {
		
		this.serverConnStrings = serverConnStrings;
		
        this.eventLoop = new NioEventLoopGroup(10);
        this.serverBootstrap = new Bootstrap();
		this.serverBootstrap.group(eventLoop).channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.handler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(
						//new LoggingHandler(LogLevel.DEBUG),
						new EchoMessageCodec(),
						new EchoProxyHandler.ServerSide(EchoProxyEngine.this));
				}
			});

		timer.schedule(new HousekeepingTask(), 0, 1000);
		
		Runtime.getRuntime().addShutdownHook(new ShutdownHook());
	}

	private class ShutdownHook extends Thread {
		public void run() {
			
			shutdown = true;
			
			long waitTime = 5000;
			long shutdownTime = System.currentTimeMillis() + waitTime;
			while(requestContextMap.isEmpty() == false) {
				
				System.out.println(String.format(
					"Shutting down, waiting for %d pending requests to complete ...", 
					requestContextMap.size()));
				
				if(System.currentTimeMillis() > shutdownTime) {
					break;
				}
				
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			int size = requestContextMap.size();
			if(size <= 0) {
				System.out.println("Completed all pending requests, shutdown!");
			}
			else {
				System.out.println(String.format(
					"Forcefully shutdown after %d ms, dropping %d pending requests!", 
					waitTime, size));
			}
			
	        eventLoop.shutdownGracefully();
		}
	}
	
	private class HousekeepingTask extends TimerTask {
		@Override
		public void run() {

			Map<String, Integer> connCountMap = new HashMap<>();
			for(Channel c : serverConnections) {
				String connString = ProxyUtil.getRemoteConnString(c);
				Integer count = connCountMap.get(connString);
				if(count == null) {
					count = 0;
					connCountMap.put(connString, count);
				}
				connCountMap.put(connString, ++count);
			}
			
			for(int i = 0; i < serverConnStrings.length; i++) {
				String[] tokens = serverConnStrings[i].split(",");
				String connString = tokens[0].trim();
				int count = Integer.valueOf(tokens[1].trim());
				Integer curCount = connCountMap.get(connString);
				if(curCount == null) {
					curCount = 0;
				}

				for(int n = 0; n < count - curCount; n++) {
					try {
						tokens = serverConnStrings[i].split(",")[0].trim().split(":");
						String host = tokens[0];
						int port = Integer.valueOf(tokens[1]);
						ChannelFuture f = serverBootstrap.connect(host, port).sync();
						serverConnections.add(f.channel());
						logger.info(String.format("Connected to %s", connString));
					}
					catch (Throwable t) {
						logger.warning("Failed to connect to server " + serverConnStrings[i]);
					}
				}
			}
		}
	}
	
	static public class TimeoutTask extends TimerTask {

		final private Logger logger = Logger.getLogger(getClass().getName());
		
		private EchoRequestContext ctx;
		
		public TimeoutTask(EchoRequestContext ctx) {
			this.ctx = ctx;
			this.ctx.timeoutTask = this;
		}
		
		@Override
		public void run() {
			if(ctx.reqeustContextMap.remove(ctx.getId()) != null) {
				logger.fine(String.format("TimerTask removed context %s", ctx.getId()));
				ProxyUtil.sendError(ctx.clientChannel, ctx.requestId, "Timed out!");
			}
		}
		
	}
	
	public void disconnectedFromServer(Channel channel) {
		String connString = ProxyUtil.getRemoteConnString(channel);
		serverConnections.remove(channel);
		logger.info(String.format("Disconnected from %s", connString));
	}
	
	public void disconnectedFromClient(Channel channel) {
		String connString = ProxyUtil.getRemoteConnString(channel);
		logger.info(String.format("Disconnected from %s", connString));
	}
	
	public void connectedToClient(Channel channel) {
		if(shutdown) {
			logger.info(String.format("Shutting down, close connection to %s", 
					ProxyUtil.getRemoteConnString(channel)));
			channel.close();
		}
	}
	
	public void receivedMessageFromClient(Channel clientChannel, EchoRequest request) {
		EchoRequestContext ctx = null;
		try {
			logger.fine("Received request from client: " + request.getId());
			if(shutdown) {
				ProxyUtil.sendError(clientChannel, request.getId(), "Proxy shutting down");
			}
			
			Channel serverChannel = getNextServerChannel();
			if(serverChannel != null) {
				ctx = new EchoRequestContext(clientChannel, serverChannel, request, requestContextMap);
				timer.schedule(ctx.timeoutTask, new Date(ctx.timeoutTime));
				logger.fine(String.format("Scheduled timeout task at %s", new Date(ctx.timeoutTime)));
				requestContextMap.put(ctx.getId(), ctx);
				request.setUserData(ProxyUtil.getRemoteConnString(clientChannel));
				ProxyUtil.sendMessage(serverChannel, request);
				logger.fine("Forwarded request to server: " + request.getId());
			}
			else {
				ProxyUtil.sendError(clientChannel, request.getId(), "No server available");
			}
		} 
		catch (Throwable t) {
			String connString = ProxyUtil.getRemoteConnString(clientChannel);
			logger.severe(String.format("Failed to process request from %s: %s", connString, t.getMessage()));
			ProxyUtil.sendError(clientChannel, t.getMessage());
			if(ctx != null) {
				ctx.timeoutTask.cancel();
				logger.fine(String.format("Cancelled timeout task at %s", new Date(ctx.timeoutTime)));
			}
		}
	}
	
	public void receivedMessageFromServer(Channel serverChannel, EchoResponse response) {
		EchoRequestContext ctx = null;
		try {
			logger.fine("Received resposne from server: " + response.getRequestId());
			String clientConnId = response.getUserData();
			String ctxId = EchoRequestContext.getContextId(clientConnId, serverChannel, response.getRequestId());
			ctx = requestContextMap.remove(ctxId);
			if(ctx == null) {
				logger.warning(String.format("Failed to find matching request for response %s from %s",
					response.getRequestId(), ProxyUtil.getRemoteConnString(serverChannel)));
				return;
			}
			ProxyUtil.sendMessage(ctx.clientChannel, response);
			logger.fine("Forwarded response to client: " + response.getRequestId());
			
			float t = 1.0f * (System.nanoTime() - ctx.startTime) / 1000000;
			String message = String.format("%s => %s %d %.2f ms", 
				ProxyUtil.getRemoteConnString(ctx.clientChannel), 
				ProxyUtil.getRemoteConnString(ctx.serverChannel), 
				ctx.requestId, t);
			System.out.println(message);
		}
		catch (Throwable t) {
			if(ctx != null) {
				ProxyUtil.sendError(ctx.clientChannel, t.getMessage());
			}
		}
		finally {
			if(ctx != null) {
				ctx.timeoutTask.cancel();
				logger.fine(String.format("Cancelled timeout task: %d", ctx.requestId));
			}
		}
	}

	private int serverSelectionIndex;
	private Channel getNextServerChannel() {
		serverSelectionIndex = ++serverSelectionIndex % serverConnections.size(); 
		return serverConnections.get(serverSelectionIndex);
	}
}
