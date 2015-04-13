package com.weisong.test.util;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import java.util.Random;
import java.util.logging.Logger;

import com.weisong.test.message.EchoMessage;
import com.weisong.test.message.EchoResponse;

public class ProxyUtil {
	
    static private final Logger logger = Logger.getLogger(ProxyUtil.class.getName());
    
    static private final Random random = new Random();
	
	static public String getConnString(SocketAddress address) {
		String addrStr = address.toString();
		if(addrStr.contains("/")) {
			addrStr = addrStr.substring(addrStr.indexOf("/") + 1);
		}
		return addrStr;
	}
	
	static public String getLocalConnString(Channel channel) {
		return getConnString(channel.localAddress());
	}
	
	static public String getRemoteConnString(Channel channel) {
		return getConnString(channel.remoteAddress());
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
				msg.getClass().getSimpleName(), getRemoteConnString(channel)));
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
