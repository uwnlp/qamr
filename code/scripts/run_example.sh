#!/bin/bash

BASE=`dirname $0`/..
pushd $BASE
{ echo ":load scripts/init_example.scala" & cat <&0; } | sbt "project exampleJVM" console
popd
