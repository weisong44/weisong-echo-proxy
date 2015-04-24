package com.weisong.test.message;

import java.util.concurrent.atomic.AtomicLong;

public class EchoRequest extends EchoMessage {

	static public AtomicLong seq = new AtomicLong(); 

	private long id = seq.incrementAndGet();
	private String message;
	private long timeout = 100L;

	protected EchoRequest() {
	}

	public EchoRequest(String message) {
		this.message = message;
	}

	public EchoRequest(String message, long timeout) {
		this(message);
		this.timeout = timeout;
	}

	public long getId() {
		return id;
	}

	public String getMessage() {
		return message;
	}

	public long getTimeout() {
		return timeout;
	}
}
