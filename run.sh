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
    echo "Running... (Ctrl-C to stop server)"
    if [ $1 == "dev" ]; then
      ./sbt "run 3050"
    else
      ./sbt "start 3050"
    fi
else
    echo "run.sh can only be run from $($rl $workdir)"
fi
