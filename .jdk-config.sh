#!/bin/bash
# Configuration JDK 17 permanente
export JAVA_HOME="/c/Program Files/Java/jdk-17"
export PATH="/c/Program Files/Java/jdk-17/bin:$PATH"

# Vérification
echo "JDK configuré sur la version :"
java -version