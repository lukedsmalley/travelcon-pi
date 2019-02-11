#!/bin/bash -e
scp -i deploy_rsa ./build/libs/travelconpi-1.0-SNAPSHOT-all.jar pi@raspberrypi:~/travelconpi.jar