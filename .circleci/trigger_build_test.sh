#!/usr/bin/env bash

curl --user ${CIRCLE_TOKEN}: \
    --request POST \
    --form revision=036328d31efa218559ba6d4418b325c71b05e1da \
    --form config=@config.yml \
    --form notify=false \
        https://circleci.com/api/v1.1/project/github/smartthingsoss/smartthings-brave-scala/tree/master
