package com.weisong.test.proxy.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.weisong.test.message.EchoRequest;
import com.weisong.test.message.EchoResponse;
import com.weisong.test.util.ProxyUtil;

public class EchoClientHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            EchoClientHandler.class.getName());
    
    static private int index;
    final static private AtomicInteger count = new AtomicInteger();

    static private long startTime = -1L;
    
    final private Worker worker = new Worker();

    public class Worker extends Thread {
		
		private Object lock = new Object();
		private long st;
		private Channel channel;
		
		public Worker() {
			setName("Worker-" + index++);
		}
		
		public void send() {
			if(count.incrementAndGet() > EchoClient.numberOfRequests) {
				long time = System.nanoTime() - startTime;
				float throughput = 1000000000.0f * EchoClient.numberOfRequests / time;
				System.out.println(String.format("Total time: %.2f ms", 1.0f * time / 1000000));
				System.out.println(String.format("Throughput: %.2f/s", throughput));
				System.exit(0);
			}
			
			synchronized (lock) {
				lock.notifyAll();
			}
		}
		
		public void received(ChannelHandlerContext ctx, EchoResponse response) {
			float t = 1.0f * (System.nanoTime() - st) / 1000000;
		    System.out.println(String.format("[%.2f][%s][%s] Received response: %d %s %.2f ms",
	    		getElapsedTime(startTime), getName(), ProxyUtil.getLocalConnString(ctx.channel()),
	    		response.getRequestId(), response.getHasError(), t));
		    send();
		}
		
		public void run() {
			
			if(startTime < 0L) {
				startTime = System.nanoTime();
			}
			
			while (true) {
				try {
					synchronized (lock) {
						lock.wait();
					}

					st = System.nanoTime();
					EchoRequest request = new EchoRequest("Ping");
					ProxyUtil.sendMessage(channel, request);
					System.out.println(String.format("[%.2f][%s] Send request: %d", 
						getElapsedTime(startTime), getName(), request.getId()));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		private float getElapsedTime(long startTime) {
			return 1.0f * (System.nanoTime() - startTime) / 1000000;
		}
	}
    
    public EchoClientHandler() {
    	worker.start();
    }
    
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
    	worker.channel = ctx.channel();
    	worker.send();
    }

    @Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		System.exit(0);
	}

	@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    	if(msg instanceof EchoResponse) {
    		EchoResponse response = (EchoResponse) msg;
    		worker.received(ctx, response);
    	}
    	
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.log(Level.WARNING, "Unexpected exception from downstream.", cause);
        ctx.close();
    }
}
