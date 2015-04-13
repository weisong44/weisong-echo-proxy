package com.weisong.test.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;
import java.util.logging.Logger;

import com.weisong.test.message.EchoMessage;
import com.weisong.test.util.JsonUtil;
import com.weisong.test.util.ProxyUtil;

public class EchoMessageCodec extends ByteToMessageCodec<EchoMessage> {
	
	final static private byte LENGTH_BEG = 62; // '>'
	final static private byte LENGTH_END = 60; // '<'

    private final Logger logger = Logger.getLogger(getClass().getName());
	
	@Override
	protected void encode(ChannelHandlerContext ctx, EchoMessage msg, ByteBuf out) throws Exception {
		String json = JsonUtil.toJsonString(msg);
		// Write the length
		out.writeByte(LENGTH_BEG);
		out.writeInt(json.getBytes().length);
		out.writeByte(LENGTH_END);
		// Write the payload
		out.writeBytes(json.getBytes());
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		while(in.isReadable()) {
			// Read length begin
			if(readLengthBegin(ctx, in) == false) {
				return;
			}
			
			// Read length
			int begin = in.readerIndex() - 1;
			if(in.readableBytes() < 5) {
				in.readerIndex(begin);
				return;
			}
			
			int length = in.readInt();
			if(length > 1000000) {
				logger.warning(String.format("%s Frame length too large, discard 1 byte: %d", 
					ProxyUtil.getRemoteConnString(ctx.channel()), length));
				in.readerIndex(begin + 1);
				continue;
			}
			
			// Read length end
			if(readLengthEnd(in) == false) {
				in.readerIndex(begin + 1); // shift by one and try again
				continue;
			}
			
			// If not enough byte, leave it to next time
			if(in.readableBytes() < length) {
				in.readerIndex(begin);
				return;
			}
			
			byte[] bytes = new byte[length];
			in.readBytes(bytes);
			String json = new String(bytes);
			
			try {
				int start = json.indexOf(EchoMessage.classNameMarker) + 3;
				int end = json.indexOf(EchoMessage.classNameMarker, start);
				String className = json.substring(start, end);
				Class<?> clazz = Class.forName(className);
				out.add(JsonUtil.toObject(json, clazz));
			} 
			catch (Throwable t) {
				in.readerIndex(begin + length + 6);
				logger.warning(String.format(
					"%s Failed to decode payload, discard %d bytes: %s", 
					ProxyUtil.getRemoteConnString(ctx.channel()), length + 6, json));
			}
		}
	}
	
	private boolean readLengthBegin(ChannelHandlerContext ctx, ByteBuf in) {
		int skipCount = 0;
		while(in.isReadable()) {
			byte nb = in.readByte();
			if(LENGTH_BEG == nb) {
				if(skipCount > 0) {
					logger.warning(String.format(
						"%s Discarded %d bytes to find frame beginning", 
						ProxyUtil.getRemoteConnString(ctx.channel()), skipCount));
				}
				return true;
			}
			++skipCount;
		}
		if(skipCount > 0) {
			logger.warning(String.format(
				"%s Discarded %d bytes, still missing frame beginning", 
				ProxyUtil.getRemoteConnString(ctx.channel()), skipCount));
		}
		return false;
	}
	
	private boolean readLengthEnd(ByteBuf in) {
		return in.readByte() == LENGTH_END;
	}
	
}
