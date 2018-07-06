#!/bin/bash

BASE=`dirname $0`/..
pushd $BASE
sbt "project analysis" "runMain qamr.analysis.WriteSquadData"
popd
