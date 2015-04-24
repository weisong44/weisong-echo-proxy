package com.weisong.test.proxy;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import io.netty.channel.Channel;

import com.weisong.test.message.EchoRequest;
import com.weisong.test.proxy.EchoProxyEngine.TimeoutTask;
import com.weisong.test.util.ProxyUtil;


public class EchoRequestContext {
	
	public long startTime = System.nanoTime();
	public long timeout = 100L;
	public long requestId;
	public Channel clientChannel;
	public Channel serverChannel;
	public TimeoutTask timeoutTask;
	public ScheduledFuture<?> timeoutTaskFuture;
	public Map<String, EchoRequestContext> reqeustContextMap;

	static public String getContextId(Channel clientChannel, Channel serverChannel, long requestId) {
		String clientConnId = ProxyUtil.getRemoteConnString(clientChannel); 
		return getContextId(clientConnId, serverChannel, requestId);
	}

	static public String getContextId(String clientConnId, Channel serverChannel, long requestId) {
		StringBuffer sb = new StringBuffer();
		sb.append(clientConnId).append(" -> ")
		  .append(ProxyUtil.getRemoteConnString(serverChannel)).append(": ")
		  .append(requestId);
		return sb.toString();
	}

	public String getId() {
		return getContextId(clientChannel, serverChannel, requestId);
	}
	
	public EchoRequestContext(Channel clientChannel, Channel serverChannel, 
			EchoRequest request, Map<String, EchoRequestContext> reqeustContextMap) {
		this.clientChannel = clientChannel;
		this.serverChannel = serverChannel;
		this.requestId = request.getId();
		this.reqeustContextMap = reqeustContextMap;
		this.timeout = request.getTimeout();
		this.timeoutTask = new TimeoutTask(this);
	}
}
