#!/bin/bash

set -e

TAG=$1

if [ -d tmp ]; then
    rm -rf tmp;
fi

mkdir tmp
git clone https://github.com/TilmanNeumann/java-math-library.git ./tmp/jml
cd tmp/jml || { echo "Cannot checkout JML"; rm -rf tmp; exit 1; }

git checkout "tags/v$TAG"

sed -i 's/<property name="ant.build.javac.source" value="1.10"\/>/<property name="ant.build.javac.source" value="17"\/>/g' build.xml
sed -i 's/<property name="ant.build.javac.target" value="1.10"\/>/<property name="ant.build.javac.target" value="17"\/>/g' build.xml

ant jar
zip -d build/jml.jar org/apache/log4j/*
mvn install:install-file "-Dfile=build/jml.jar" "-DgroupId=de.tillman_neumann" "-DartifactId=java-math-library" "-Dversion=$TAG" "-Dpackaging=jar" "-DgeneratePom=true"
cd ../..

rm -rf tmp;
