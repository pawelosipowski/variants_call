#!/bin/bash
#shred in=<infile>

usage(){
echo "
Written by Brian Bushnell
Last modified August 21, 2015
Description:  Shreds sequences into shorter, potentially overlapping sequences.

Usage:	shred.sh in=<file> out=<file> length=<number> minlength=<number> overlap=<number>


in=<file>     Input sequences.
out=<file>    Destination of output shreds.
length=500    Desired length of shreds.
minlength=1   Shortest allowed shred.  The last shred of each input sequence may be shorter than desired length.
overlap=0     Amount of overlap between successive reads.
reads=-1      If nonnegative, stop after this many input sequences.
equal=f       Shred each sequence into subsequences of equal size of at most 'length', instead of a fixed size.

Please contact Brian Bushnell at bbushnell@lbl.gov if you encounter any problems.
"
}

pushd . > /dev/null
DIR="${BASH_SOURCE[0]}"
while [ -h "$DIR" ]; do
  cd "$(dirname "$DIR")"
  DIR="$(readlink "$(basename "$DIR")")"
done
cd "$(dirname "$DIR")"
DIR="$(pwd)/"
popd > /dev/null

#DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/"
CP="$DIR""current/"

z="-Xmx1400m"
EA="-ea"
set=0

if [ -z "$1" ] || [[ $1 == -h ]] || [[ $1 == --help ]]; then
	usage
	exit
fi

calcXmx () {
	source "$DIR""/calcmem.sh"
	parseXmx "$@"
}
calcXmx "$@"

stats() {
	#module unload oracle-jdk
	#module load oracle-jdk/1.7_64bit
	#module load pigz
	local CMD="java $EA $z -cp $CP jgi.Shred $@"
#	echo $CMD >&2
	eval $CMD
}

stats "$@"
