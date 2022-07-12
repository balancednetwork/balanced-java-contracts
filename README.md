# Balanced Java Contracts

![Gradle](https://img.shields.io/badge/gradle-7.4.2-blue)
[![build](https://github.com/balancednetwork/balanced-java-contracts/actions/workflows/pr-test.yml/badge.svg?branch=main)](https://github.com/balancednetwork/balanced-java-contracts/actions/workflows/pr-test.yml)

This repository contains the smart contracts for Balanced in Java. For python contracts check [balanced-contracts](
https://github.com/balancednetwork/balanced-contracts). 

## Setting up Local Environment

- Clone this repo with submodules

```shell
  $ git clone --recursive git@github.com:balancednetwork/balanced-java-contracts.git
```

- Clone only submodules if you have already cloned the repo

```shell
$ git submodule update --init
```

- Run unit tests

```shell
./gradlew clean build optimizedJar
```

## Running integration tests

- Install [docker](https://docs.docker.com/engine/install/) and [docker-compose](https://docs.docker.com/compose/install/)
- Start local blockchain
```shell
$ docker-compose up -d
```
- Run integration test
```shell
$ ./gradlew integrationTest
```

## Local Deployment

After installing docker and docker-compose, start the local blockchain.

- Run deployment task
```shell
$ ./gradlew deployToLocal
```

## Discussion

Visit us on [Discord](https://discord.gg/5EzEtP4XQE) to discuss.