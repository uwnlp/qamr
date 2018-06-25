#!/bin/bash
# Usage:
#    get_pas.sh <annotatation-file> <output-dir>
set -e
echo "Converting from dropbox format..."
python ./convert_dropbox.py \
       --in=$1 \
       --sents=../../data/wiki-sentences.tsv \
       --out=./tmp.tok \
       --dont_use_alignment\
       --extended


echo "Aligning QA to sentence..."
python align_exp.py --in=./tmp.tok  --out=./tmp.map
echo "Extacting PAS to $2 ..."
python chunk.py --in=./tmp.map  --projective --html=$2

rm tmp.tok tmp.map
echo "Done!"
