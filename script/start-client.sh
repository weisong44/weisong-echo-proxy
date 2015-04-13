#!/bin/sh

if [ $# -ne 3 ]; then
  echo
  echo "start-client.sh <host> <port> <number-of-requests>"
  echo
  exit
fi

cd `dirname $0`/..
mvn -Djava.util.logging.config.file=config/logging.properties exec:java \
	-Dexec.mainClass="com.weisong.test.proxy.client.EchoClient" \
	-Dexec.args="$1 $2 $3"  
