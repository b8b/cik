#!/bin/sh

# PROVIDE: cikit
# REQUIRE: LOGIN
# KEYWORD: shutdown
#
# Add the following lines to /etc/rc.conf.local or /etc/rc.conf
# to enable this service:
#
# cikit_enable (bool):       Set to NO by default
#                                         Set it to YES to enable cikit
# cikit_daemonargs (string): Set additional jvm arguments
#                                         Default is "-c -u nobody"
# cikit_javavm (string):     Set path to java
#                                         Default is "@PREFIX@/openjdk8/bin/java"
# cikit_javaargs (string):   Set additional jvm arguments
#                                         Default is "-XX:CICompilerCount=2 -XX:+UseSerialGC -Xmx20m"
# cikit_args (string):       Set additional command line arguments
#                                         Default is "-config=@PREFIX@/etc/cikit.yml"

. /etc/rc.subr

name=cikit
rcvar=cikit_enable

load_rc_config $name

: ${cikit_enable:="NO"}
: ${cikit_daemonargs:="-c -u nobody"}
: ${cikit_javavm:="@PREFIX@/openjdk8/bin/java"}
: ${cikit_javaargs:="-XX:CICompilerCount=2 -XX:+UseSerialGC -Xmx20m"}
: ${cikit_args:="-config=@PREFIX@/etc/cikit.yml"}

pidfile=/var/run/cikit.pid
command="/usr/sbin/daemon"
procname="${cikit_javavm}"
command_args="-p ${pidfile} ${cikit_daemonargs} \
  ${procname} \
  -Dvertx.cacheDirBase=/tmp/.vertx \
  -cp @PREFIX@/share/cikit/cikit-@VERSION@.jar \
  ${cikit_javaargs} \
  org.cikit.core.MainKt ${cikit_args} \
  > /var/log/cikit/main.out 2>&1"

load_rc_config $name
run_rc_command "$1"
