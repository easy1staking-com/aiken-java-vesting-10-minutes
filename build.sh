#!/usr/bin/env bash

pwd

cd vesting && aiken build && cd ..

pwd

cp vesting/plutus.json java/src/main/resources/

cd java && ./gradlew clean compileJava compileTestJava

