#!/bin/bash

echo "start running the bash script"

# load java 1.8
module load java/1.8.0_91

# root directory
cd /home/zzt0009/pFogSim

# add all .java files into sources.txt 
find -name "*.java" > sources.txt

# compile the java project
# -d  path for generating .class files 
# -cp .jar files path
# @sourcefilename
javac -d bin -cp "./lib/*" @sources.txt

# run the simulator
java -cp bin edu.boun.edgecloudsim.sample_application.mainApp

