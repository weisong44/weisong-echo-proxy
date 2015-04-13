package com.weisong.test.message;

import java.util.concurrent.atomic.AtomicLong;

public class EchoRequest extends EchoMessage {
	
	static public AtomicLong seq = new AtomicLong(); 
	
	private long id = seq.incrementAndGet();
	private String message;

	protected EchoRequest() {
	}
	
	public EchoRequest(String message) {
		this.message = message;
	}
	
	public long getId() {
		return id;
	}
	
	public String getMessage() {
		return message;
	}
}
