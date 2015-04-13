package com.weisong.test.message;

import com.weisong.test.util.ProxyUtil;

public class EchoMessage {
	
	final static public String classNameMarker = "|||";
	
	private String className = String.format("%s%s%s", 
			classNameMarker, getClass().getName(), classNameMarker);
	private String userData;
	private String data = ProxyUtil.createRandomString(128); 

	public String getClassName() {
		return className;
	}

	public String getData() {
		return data;
	}

	public String getUserData() {
		return userData;
	}

	public void setUserData(String userData) {
		this.userData = userData;
	}
	
}
