#!/bin/sh

cd `dirname $0`/..
mvn -Djava.util.logging.config.file=config/logging.properties exec:java \
	-Dexec.mainClass="com.weisong.test.proxy.EchoProxy"
