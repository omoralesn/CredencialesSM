#!/bin/sh
cd "$(dirname "$0")"
exec java -Djava.library.path="$(pwd)/natives" -jar zxp-prueba.jar
