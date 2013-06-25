# Iago Echo Example

This is the README for an example usage of Iago. This brings up an echo server on your localhost,
then creates an Iago job to send traffic to your server.

If you are unfamiliar with how to use Iago, check out src & config/echo.scala. Also read the Iago README.

## Build It

mvn package

## Unpack

cd target
rm -rf yo && mkdir yo && cd yo
unzip ../iago-echo-package-dist.zip

## Start the Echo Server
sh -evx scripts/echo-server.sh

## Run Iago
java -jar iago-echo-1.0.jar -f config/echo.scala
