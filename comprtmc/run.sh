MEMLIM=8G
parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
time java 2>&1 -Xmx${MEMLIM} -Dorg.slf4j.simpleLogger.defaultLogLevel=error -jar $parent_path/target/scala-3.1.1/compRTMC-assembly-0.1.0-SNAPSHOT.jar --alg $1 --fsm $2 --ta $3

