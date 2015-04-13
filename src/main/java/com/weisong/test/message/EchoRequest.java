package com.weisong.test.message;

public class EchoRequest extends EchoMessage {
	
	static public long seq; 
	
	private long id = ++seq;
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
