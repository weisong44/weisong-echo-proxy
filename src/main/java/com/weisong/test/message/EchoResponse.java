package com.weisong.test.message;

public class EchoResponse extends EchoMessage {
	
	private long requestId;
	private String message;
	private boolean hasError;

	public EchoResponse() {
	}
	
	public EchoResponse(long requestId, String message) {
		this(requestId, message, false);
	}

	public EchoResponse(long requestId, String message, boolean hasError) {
		this.message = message;
		this.requestId = requestId;
		this.hasError = hasError;
	}

	public long getRequestId() {
		return requestId;
	}

	public String getMessage() {
		return message;
	}
	
	public boolean getHasError() {
		return hasError;
	}
	
}
