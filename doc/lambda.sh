#!/bin/bash

# aws lambda create-function \
#     --function-name roesc \
#     --handler roesc.core::handler \
#     --runtime java8 \
#     --memory 320 \
#     --timeout 15 \
#     --role arn:aws:iam::394917694455:role/service-role/helloworldrunner \
#     --zip-file fileb:///home/jgracin/src/roesc/target/uberjar/roesc-0.1.0-SNAPSHOT-standalone.jar

#aws lambda update-function-code \
#    --function-name "arn:aws:lambda:us-east-1:394917694455:function:roesc" \
#    --zip-file fileb:///home/jgracin/src/roesc/target/uberjar/roesc-0.1.0-SNAPSHOT-standalone.jar

# aws lambda invoke --function-name "arn:aws:lambda:us-east-1:394917694455:function:roesc" output.dump

# aws lambda delete-function --function-name "arn:aws:lambda:us-east-1:394917694455:function:roesc"
