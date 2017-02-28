#!/bin/bash
workdir=$(dirname $0)

if [ "$(uname -s)" == "Darwin" ]; then
	if [ -n "$(which greadlink)" ]; then
		rl="greadlink -e"
	else
		rl=readlink
	fi
else
	rl="readlink -e"
fi

if [ "$($rl $workdir)" == "$($rl $(pwd))" ]; then
    ./sbt "run 9020"
else
    echo "run.sh can only be run from $($rl $workdir)"
fi
