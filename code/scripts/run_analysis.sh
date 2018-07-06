#!/bin/bash

BASE=`dirname $0`/..
pushd $BASE
sbt "project analysis" run
popd
