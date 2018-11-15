#!/usr/bin/env bash

curl --user ${CIRCLE_TOKEN}: \
    --request POST \
    --form revision=c9f40bfeffe56ca10d43df097582c17dcb888ae7 \
    --form config=@config.yml \
    --form notify=false \
        https://circleci.com/api/v1.1/project/github/llinder/smartthings-brave-scala/tree/master