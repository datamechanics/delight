set dotenv-load := true

collector_url := 'https://api.sparklistener.lucia.montara.io/collector/'
api_key := ''
spark_distributions_folder := 'spark_distributions'

build:
    sbt +package

clean:
    sbt clean

publish:
    sbt +publishSigned

download_spark_distribution url:
    #!/usr/bin/env sh
    set -e
    mkdir -p {{spark_distributions_folder}} && cd {{spark_distributions_folder}}
    URL={{url}}
    ARCHIVE="${URL##*/}"
    FOLDER="${ARCHIVE%.*}"
    if [ ! -d "$FOLDER" ] ; then
        if [ ! -f "$ARCHIVE" ] ; then
            curl -O -L {{url}}
        else
            echo "Did not download $URL because archive $ARCHIVE exists"
        fi
        tar -zxf $ARCHIVE
    else
        echo "Did not download $URL because folder $FOLDER exists"
    fi

download_2_3_2: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-2.3.2/spark-2.3.2-bin-hadoop2.7.tgz')
download_2_4_0: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-2.4.0/spark-2.4.0-bin-hadoop2.7.tgz')
download_2_4_7: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-2.4.7/spark-2.4.7-bin-hadoop2.7.tgz')
download_2_4_7_2_12: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-2.4.7/spark-2.4.7-bin-without-hadoop-scala-2.12.tgz')
download_3_0_1: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-3.0.1/spark-3.0.1-bin-hadoop3.2.tgz')
download_3_1_1: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-3.1.1/spark-3.1.1-bin-hadoop3.2.tgz')
download_3_2_0: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-3.2.0/spark-3.2.0-bin-hadoop3.2.tgz')
download_3_3_0: (download_spark_distribution 'https://archive.apache.org/dist/spark/spark-3.3.0/spark-3.3.0-bin-hadoop3.tgz')

download_all_spark_distributions: download_2_3_2 download_2_4_0 download_2_4_7 download_2_4_7_2_12 download_3_0_1 download_3_1_1 download_3_2_0 download_3_3_0

run_test_app spark_distribution_folder spark_version scala_version:
    #!/bin/bash

    if [ "$(git branch --show-current)" = "main" ]; then
        VERSION="latest"
    else
        VERSION=$(git branch --show-current)
    fi

    {{spark_distribution_folder}}/bin/spark-submit \
    --class org.apache.spark.examples.SparkPi \
    --master 'local[*]' \
    --packages io.montara.lucia:sparklistener_{{scala_version}}:${VERSION}-SNAPSHOT \
    --repositories https://oss.sonatype.org/content/repositories/snapshots \
    --conf spark.lucia.sparklistener.accessToken.secret={{api_key}} \
    --conf spark.lucia.sparklistener.collector.url={{collector_url}} \
    --conf spark.extraListeners=io.montara.lucia.sparklistener.LuciaSparkListener \
    {{spark_distribution_folder}}/examples/jars/spark-examples_{{scala_version}}-{{spark_version}}.jar \
    100

run_test_app_local_jar spark_distribution_folder spark_version scala_version:
    #!/bin/bash

    if [ "$(git branch --show-current)" = "main" ]; then
        VERSION="latest"
    else
        VERSION=$(git branch --show-current)
    fi

    {{spark_distribution_folder}}/bin/spark-submit \
    --class org.apache.spark.examples.SparkPi \
    --master 'local[*]' \
    --jars agent/target/scala-{{scala_version}}/sparklistener_{{scala_version}}-${VERSION}-SNAPSHOT.jar \
    --conf spark.lucia.sparklistener.accessToken.secret={{api_key}} \
    --conf spark.lucia.sparklistener.collector.url={{collector_url}} \
    --conf spark.lucia.sparklistener.logDuration=true \
    --conf spark.extraListeners=io.montara.lucia.sparklistener.LuciaSparkListener \
    {{spark_distribution_folder}}/examples/jars/spark-examples_{{scala_version}}-{{spark_version}}.jar \
    100

run_test_app_docker image spark_version scala_version:
    #!/bin/bash

    if [ "$(git branch --show-current)" = "main" ]; then
        VERSION="latest"
    else
        VERSION=$(git branch --show-current)
    fi

    docker run --rm \
    -v $(pwd)/agent/target/scala-{{scala_version}}/sparklistener_{{scala_version}}-${VERSION}-SNAPSHOT.jar:/opt/spark/delight.jar \
    {{image}} \
    /opt/spark/bin/spark-submit --class org.apache.spark.examples.SparkPi --master 'local[*]' \
    --jars /opt/spark/delight.jar \
    --conf spark.lucia.sparklistener.accessToken.secret={{api_key}} \
    --conf spark.lucia.sparklistener.collector.url={{collector_url}} \
    --conf spark.lucia.sparklistener.logDuration=true \
    --conf spark.extraListeners=io.montara.lucia.sparklistener.LuciaSparkListener \
    /opt/spark/examples/jars/spark-examples_{{scala_version}}-{{spark_version}}.jar \
    100



run_2_3_2: (run_test_app 'spark_distributions/spark-2.3.2-bin-hadoop2.7' '2.3.2' '2.11')
run_2_4_0: (run_test_app 'spark_distributions/spark-2.4.0-bin-hadoop2.7' '2.4.0' '2.11')
run_2_4_7: (run_test_app 'spark_distributions/spark-2.4.7-bin-hadoop2.7' '2.4.7' '2.11')
run_2_4_7_2_12: (run_test_app 'spark_distributions/spark-2.4.7-bin-without-hadoop-scala-2.12' '2.4.7' '2.12')
run_3_0_1: (run_test_app 'spark_distributions/spark-3.0.1-bin-hadoop3.2' '3.0.1' '2.12')
run_3_1_1: (run_test_app 'spark_distributions/spark-3.1.1-bin-hadoop3.2' '3.1.1' '2.12')
run_3_2_0: (run_test_app 'spark_distributions/spark-3.2.0-bin-hadoop3.2' '3.2.0' '2.12')
run_3_3_0: (run_test_app 'spark_distributions/spark-3.3.0-bin-hadoop3' '3.3.0' '2.12')

run_local_jar_2_3_2: (run_test_app_local_jar 'spark_distributions/spark-2.3.2-bin-hadoop2.7' '2.3.2' '2.11')
run_local_jar_2_4_0: (run_test_app_local_jar 'spark_distributions/spark-2.4.0-bin-hadoop2.7' '2.4.0' '2.11')
run_local_jar_2_4_7: (run_test_app_local_jar 'spark_distributions/spark-2.4.7-bin-hadoop2.7' '2.4.7' '2.11')
run_local_jar_2_4_7_2_12: (run_test_app_local_jar 'spark_distributions/spark-2.4.7-bin-without-hadoop-scala-2.12' '2.4.7' '2.12')
run_local_jar_3_0_1: (run_test_app_local_jar 'spark_distributions/spark-3.0.1-bin-hadoop3.2' '3.0.1' '2.12')
run_local_jar_3_1_1: (run_test_app_local_jar 'spark_distributions/spark-3.1.1-bin-hadoop3.2' '3.1.1' '2.12')
run_local_jar_3_2_0: (run_test_app_local_jar 'spark_distributions/spark-3.2.0-bin-hadoop3.2' '3.2.0' '2.12')
run_local_jar_3_3_0: (run_test_app_local_jar 'spark_distributions/spark-3.3.0-bin-hadoop3' '3.3.0' '2.12')

run_docker_3_3_0: (run_test_app_docker 'montara/spark:jvm-only-3.3.0-dm18' '3.3.0' '2.12')
run_docker_3_2_0: (run_test_app_docker 'montara/spark:jvm-only-3.2.0-dm15' '3.2.0' '2.12')
run_docker_3_1_1: (run_test_app_docker 'montara/spark:jvm-only-3.1.1-dm12' '3.1.1' '2.12')
run_docker_3_0_1: (run_test_app_docker 'montara/spark:jvm-only-3.0.1-dm12' '3.0.1' '2.12')
run_docker_2_4_7: (run_test_app_docker 'montara/spark:jvm-only-2.4.7-hadoop-3.1.0-java-8-scala-2.11-dm12' '2.4.7' '2.11')
