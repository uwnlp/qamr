#!/bin/bash

# Setup script for the QAMR project.
# This script is idempotent; don't be afraid to run it multiple times.
# Downloads all of the requisite data except the Penn Treebank.

# NOTE: If you already have any of the datasets somewhere, go just symlink
# datasets/<dirname> to it (for whatever dirname it expects in this script)
# so it doesn't have to be downloaded again.

BASE=`dirname $0`/..
pushd $BASE

# initialize submodules
git submodule update --init --recursive
# publish local dependencies
echo "Publishing nlpdata locally..."
pushd lib/nlpdata
sbt publishLocal
popd
echo "Publishing spacro locally..."
pushd lib/spacro
sbt publishLocal
popd

if [ ! -e "datasets/ptb/" ]
then
    echo "-WARNING- Please download the Penn Treebank (version 2) and place it at datasets/ptb in order to reproduce the original QAMR data collection pipeline or the data analysis. Relevant projects: example, analysis"
    echo "It requires an LDC license. Webpage: https://catalog.ldc.upenn.edu/ldc99t42"
fi

if [ ! -e "datasets/ontonotes-release-5.0/" ]
then
    echo "-WARNING- Please download Ontonotes 5 and place it at datasets/ontonotes-release-5.0 in order to reproduce the data analysis. Relevant projects: analysis"
    echo "It requires an LDC license. Webpage: https://catalog.ldc.upenn.edu/ldc2013t19"
fi

if [ ! -e "datasets/wiki1k/" ]
then
    read -p $'Download Wiki1k data? (relevant projects: example, analysis) [y/N]\n' answer
    case ${answer:0:1} in
        y|Y )
            wget https://www.dropbox.com/s/j5rmbppa4mk6hit/wiki1k.tar.gz?dl=1 \
                 -O wiki1k.tar.gz
            tar zxvf wiki1k.tar.gz
            rm wiki1k.tar.gz
            mv wiki1k datasets/wiki1k
            ;;
        * )
            echo "Skipping Wiki1k. Run setup.sh again if you change your mind."
            ;;
    esac
fi

if [ ! -e "datasets/wiktionary/" ]
then
    read -p $'Download the Wiktionary data? (relevant projects: analysis) [y/N]\n' answer
    case ${answer:0:1} in
        y|Y )
            wget \
                --no-check-cert https://www.dropbox.com/s/60hbl3py7g3tx12/wiktionary.tar.gz?dl=1 \
                -O wiktionary.tar.gz
            tar zxvf wiktionary.tar.gz
            rm wiktionary.tar.gz
            mv wiktionary datasets/wiktionary
            ;;
        * )
            echo "Skipping Wiktionary. Run setup.sh again if you change your mind."
            ;;
    esac
fi

if [ ! -e "datasets/propbank/" ]
then
    read -p $'Download PropBank? (relevant projects: analysis) [y/N]\n' answer
    case ${answer:0:1} in
        y|Y )
            pushd datasets
            git clone https://github.com/propbank/propbank-release.git
            mv propbank-release propbank
            popd
            ;;
        * )
            echo "Skipping PropBank. Run setup.sh again if you change your mind."
            ;;
    esac
fi

if [ -e "datasets/propbank" ]
then
    if [ ! -e "datasets/propbank/conversion_complete" ]
    then
        if [ -e "datasets/ontonotes-release-5.0" ]
        then
            pushd datasets/propbank/docs/scripts
            python2 map_all_to_conll.py --ontonotes ../../../ontonotes-release-5.0 && touch ../../conversion_complete
            popd
        else
            echo "-WARNING- In order to do the PropBank SRL comparison, the gold CoNLL files need to be converted from Ontonotes 5. Please download Ontonotes 5 and re-run the setup script to do the conversion. Relevant projects: analysis"
        fi
    fi
fi

if [ ! -e "datasets/nombank.1.0/" ]
then
    read -p $'Download NomBank? (relevant projects: analysis) [y/N]\n' answer
    case ${answer:0:1} in
        y|Y )
            wget http://nlp.cs.nyu.edu/meyers/nombank/nombank.1.0.tgz \
                 -O nombank.1.0.tgz
            tar zxvf nombank.1.0.tgz
            rm nombank.1.0.tgz
            mv nombank.1.0 datasets/nombank.1.0
            ;;
        * )
            echo "Skipping NomBank. Run setup.sh again if you change your mind."
            ;;
    esac
fi

if [ ! -e "datasets/qasrl/" ]
then
    read -p $'Download the QA-SRL data? (relevant projects: analysis) [y/N]\n' answer
    case ${answer:0:1} in
        y|Y )
            wget https://www.dropbox.com/s/dvfk6rhiuzc5rmw/qasrl.tar.gz?dl=1 \
                 -O qasrl.tar.gz
            tar zxvf qasrl.tar.gz
            rm qasrl.tar.gz
            mv qasrl datasets/qasrl
            ;;
        * )
            echo "Skipping QA-SRL. Run setup.sh again if you change your mind."
            ;;
    esac
fi

popd
