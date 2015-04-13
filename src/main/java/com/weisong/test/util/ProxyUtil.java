package com.weisong.test.util;

import io.netty.channel.Channel;

import java.util.Random;
import java.util.logging.Logger;

import com.weisong.test.message.EchoMessage;
import com.weisong.test.message.EchoResponse;

public class ProxyUtil {
	
    static private final Logger logger = Logger.getLogger(ProxyUtil.class.getName());
    
    static private final Random random = new Random();
	
	static public String getConnString(Channel channel) {
		String remoteAddr = channel.remoteAddress().toString();
		if(remoteAddr.contains("/")) {
			remoteAddr = remoteAddr.substring(remoteAddr.indexOf("/") + 1);
		}
		return remoteAddr;
	}
	
	static public void sendError(Channel channel, String errMsg) {
		sendError(channel, -1L, errMsg);
	}
	
	static public void sendError(Channel channel, long requestId, String errMsg) {
		if(errMsg == null) {
			errMsg = "Unknown error";
		}
		EchoResponse error = new EchoResponse(requestId, errMsg, true);
		sendMessage(channel, error);
	}

	static public void sendMessage(Channel channel, EchoMessage msg) {
		try {
			channel.writeAndFlush(msg);
		} catch (Exception e) {
			logger.severe(String.format("Failed to send %s to %s", 
				msg.getClass().getSimpleName(), getConnString(channel)));
		}
	}
	
	static public String createRandomString(int length) {
		byte[] bytes = new byte[length];
		for(int i = 0; i < length; i++) {
			bytes[i] = (byte) (32 + random.nextInt(90));
		}
		return new String(bytes);
	}
}
