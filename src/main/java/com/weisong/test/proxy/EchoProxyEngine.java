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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.weisong.test.codec.EchoMessageCodec;
import com.weisong.test.message.EchoRequest;
import com.weisong.test.message.EchoResponse;
import com.weisong.test.util.ProxyUtil;

public class EchoProxyEngine {

	final static private SimpleDateFormat df = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS z");
	
	final private Logger logger = Logger.getLogger(getClass().getName());

	// Shutdown flag
	private boolean shutdown;
	
	// The Timer
	private Timer timer = new Timer();
	
	// Scheduling executor
	ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
	
	// Server connection string and connections
	private String[] serverConnStrings;
	// Server connection map
	private ArrayList<Channel> serverConnections = new ArrayList<>();
	// Index to select a server
	private int serverSelectionIndex;

	// Request map
	private Map<String, EchoRequestContext> requestContextMap = new ConcurrentHashMap<>();

	// The server connection stack
	private Bootstrap serverBootstrap;
    private EventLoopGroup eventLoop = new NioEventLoopGroup();
    
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
			
			System.out.println(String.format(
					"[%s] Shutdown initiated", 
					df.format(new Date()), requestContextMap.size()));
			
			long waitTime = 5000;
			long shutdownTime = System.currentTimeMillis() + waitTime;
			while(requestContextMap.isEmpty() == false) {
				
				System.out.println(String.format(
					"[%s] Waiting for %d pending requests to complete ...", 
					df.format(new Date()), requestContextMap.size()));
				
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
				System.out.println(String.format(
					"[%s] Completed all pending requests, shutdown gracefully!", 
					df.format(new Date())));
			}
			else {
				System.out.println(String.format(
					"[%s] Forcefully shutdown after %d ms, dropping %d pending requests!", 
					df.format(new Date()), waitTime, size));
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
	
	static public class TimeoutTask implements Runnable {

		final private Logger logger = Logger.getLogger(getClass().getName());

		private EchoRequestContext ctx;
		
		public TimeoutTask(EchoRequestContext ctx) {
			this.ctx = ctx;
		}
		
		@Override
		public void run() {
			if(ctx.reqeustContextMap.remove(ctx.getId()) != null) {
				logger.fine(String.format("TimerTask removed context %s", ctx.getId()));
				ProxyUtil.sendError(ctx.clientChannel, ctx.requestId, "Timed out!");
				printAccessLog(ctx, true);
			}
		}
		
	}
	
	public void connectedToClient(Channel channel) {
		if(shutdown) {
			logger.info(String.format("Shutting down, close connection to %s", 
					ProxyUtil.getRemoteConnString(channel)));
			channel.close();
		}
		else {
			String connString = ProxyUtil.getRemoteConnString(channel);
			logger.info(String.format("Connected client: %s", connString));
		}
	}
	
	public void disconnectedFromClient(Channel channel) {
		String connString = ProxyUtil.getRemoteConnString(channel);
		logger.info(String.format("Disconnected client: %s", connString));
	}
	
	public void disconnectedFromServer(Channel channel) {
		String connString = ProxyUtil.getRemoteConnString(channel);
		serverConnections.remove(channel);
		logger.info(String.format("Disconnected server: %s", connString));
	}
	
	public void receivedMessageFromClient(Channel clientChannel, EchoRequest request) {
		EchoRequestContext ctx = null;
		try {
			logger.fine("Received request from client: " + request.getId());
			if(shutdown) {
				ProxyUtil.sendError(clientChannel, request.getId(), "Proxy shutting down");
				printAccessLog(clientChannel, null, System.nanoTime(), request.getId(), true);
				return;
			}
			
			Channel serverChannel = getNextServerChannel();
			if(serverChannel != null) {
				ctx = new EchoRequestContext(clientChannel, serverChannel, request, requestContextMap);
				requestContextMap.put(ctx.getId(), ctx);
				request.setUserData(ProxyUtil.getRemoteConnString(clientChannel));
				ProxyUtil.sendMessage(serverChannel, request);
				ctx.timeoutTaskFuture = executor.schedule(
						new TimeoutTask(ctx), ctx.timeout, TimeUnit.MILLISECONDS);
				logger.fine(String.format("Scheduled timeout task at %s", 
						new Date(System.currentTimeMillis() + ctx.timeout)));
				logger.fine("Forwarded request to server: " + request.getId());
			}
			else {
				ProxyUtil.sendError(clientChannel, request.getId(), "No server available");
				printAccessLog(ctx, true);
			}
		} 
		catch (Throwable t) {
			String connString = ProxyUtil.getRemoteConnString(clientChannel);
			logger.severe(String.format("Failed to process request from %s: %s", connString, t.getMessage()));
			ProxyUtil.sendError(clientChannel, t.getMessage());
			printAccessLog(ctx, true);
			if(ctx != null && ctx.timeoutTaskFuture != null) {
				ctx.timeoutTaskFuture.cancel(true);
				logger.fine(String.format("Cancelled timeout task"));
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
				logger.fine(String.format("Failed to find matching request for response %s from %s",
					response.getRequestId(), ProxyUtil.getRemoteConnString(serverChannel)));
				return;
			}
			ProxyUtil.sendMessage(ctx.clientChannel, response);
			printAccessLog(ctx, response.getHasError());
			logger.fine("Forwarded response to client: " + response.getRequestId());
		}
		catch (Throwable t) {
			if(ctx != null) {
				ProxyUtil.sendError(ctx.clientChannel, t.getMessage());
				printAccessLog(ctx, true);
			}
		}
		finally {
			if(ctx != null && ctx.timeoutTaskFuture != null) {
				ctx.timeoutTaskFuture.cancel(true);
				logger.fine(String.format("Cancelled timeout task: %d", ctx.requestId));
			}
		}
	}

	private Channel getNextServerChannel() {
		serverSelectionIndex = ++serverSelectionIndex % serverConnections.size(); 
		return serverConnections.get(serverSelectionIndex);
	}
	
	static private void printAccessLog(EchoRequestContext ctx, boolean error) {
		printAccessLog(ctx.clientChannel, ctx.serverChannel, ctx.startTime, ctx.requestId, error);
	}
	
	static private void printAccessLog(Channel clientChannel, Channel serverChannel, 
			long startTime, long requestId, boolean error) {
		String serverConnString = serverChannel == null ? 
				"Unknown" : ProxyUtil.getRemoteConnString(serverChannel);
		String result = error ? "ER" : "OK";
		float t = 1.0f * (System.nanoTime() - startTime) / 1000000;
		String message = String.format("[%s][%s] %s => [P] => %s %d %.2f ms",
			df.format(new Date()), result,
			ProxyUtil.getRemoteConnString(clientChannel), 
			serverConnString, 
			requestId, t);
		System.out.println(message);
	}
}
