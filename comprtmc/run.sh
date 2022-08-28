MEMLIM=8G
if [ "$#" -lt 3 ]; then
    echo "Illegal number of parameters"
    exit -1
fi
verbose=false
if [ "$#" -eq 4 ]; then
	if [ $4 == "verbose" ]; then
		verbose=true
	fi
fi

parent_path=$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )
PATH=$PATH:$parent_path/resources/scripts/aig/ time java 2>&1 -Xmx${MEMLIM} -Dorg.slf4j.simpleLogger.defaultLogLevel=error -jar $parent_path/target/scala-3.1.1/compRTMC-assembly-0.1.0-SNAPSHOT.jar --alg $1 --fsm $2 --ta $3 --verbose $verbose
