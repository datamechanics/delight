collector_url := 'https://api.delight.datamechanics.co/collector/'
api_key := ''
spark_distributions_folder := 'spark_distributions'

build:
    sbt +package

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

download_all_spark_distributions: download_2_3_2 download_2_4_0 download_2_4_7 download_2_4_7_2_12 download_3_0_1 download_3_1_1

run_test_app spark_distribution_folder spark_version scala_version:
    {{spark_distribution_folder}}/bin/spark-submit \
    --class org.apache.spark.examples.SparkPi \
    --master 'local[2]' \
    --packages co.datamechanics:delight_{{scala_version}}:latest-SNAPSHOT \
    --repositories https://oss.sonatype.org/content/repositories/snapshots \
    --conf spark.delight.accessToken.secret={{api_key}} \
    --conf spark.delight.collector.url={{collector_url}} \
    --conf spark.extraListeners=co.datamechanics.delight.DelightListener \
    {{spark_distribution_folder}}/examples/jars/spark-examples_{{scala_version}}-{{spark_version}}.jar \
    100

run_test_app_local_jar spark_distribution_folder spark_version scala_version:
    {{spark_distribution_folder}}/bin/spark-submit \
    --class org.apache.spark.examples.SparkPi \
    --master 'local[2]' \
    --jars target/scala-{{scala_version}}/delight_{{scala_version}}-latest-SNAPSHOT.jar \
    --repositories https://oss.sonatype.org/content/repositories/snapshots \
    --conf spark.delight.accessToken.secret={{api_key}} \
    --conf spark.delight.collector.url={{collector_url}} \
    --conf spark.extraListeners=co.datamechanics.delight.DelightListener \
    {{spark_distribution_folder}}/examples/jars/spark-examples_{{scala_version}}-{{spark_version}}.jar \
    100

run_2_3_2: (run_test_app 'spark_distributions/spark-2.3.2-bin-hadoop2.7' '2.3.2' '2.11')
run_2_4_0: (run_test_app 'spark_distributions/spark-2.4.0-bin-hadoop2.7' '2.4.0' '2.11')
run_2_4_7: (run_test_app 'spark_distributions/spark-2.4.7-bin-hadoop2.7' '2.4.7' '2.11')
run_2_4_7_2_12: (run_test_app 'spark_distributions/spark-2.4.7-bin-without-hadoop-scala-2.12' '2.4.7' '2.12')
run_3_0_1: (run_test_app 'spark_distributions/spark-3.0.1-bin-hadoop3.2' '3.0.1' '2.12')
run_3_1_1: (run_test_app 'spark_distributions/spark-3.1.1-bin-hadoop3.2' '3.1.1' '2.12')

run_local_jar_2_3_2: (run_test_app_local_jar 'spark_distributions/spark-2.3.2-bin-hadoop2.7' '2.3.2' '2.11')
run_local_jar_2_4_0: (run_test_app_local_jar 'spark_distributions/spark-2.4.0-bin-hadoop2.7' '2.4.0' '2.11')
run_local_jar_2_4_7: (run_test_app_local_jar 'spark_distributions/spark-2.4.7-bin-hadoop2.7' '2.4.7' '2.11')
run_local_jar_2_4_7_2_12: (run_test_app_local_jar 'spark_distributions/spark-2.4.7-bin-without-hadoop-scala-2.12' '2.4.7' '2.12')
run_local_jar_3_0_1: (run_test_app_local_jar 'spark_distributions/spark-3.0.1-bin-hadoop3.2' '3.0.1' '2.12')
run_local_jar_3_1_1: (run_test_app_local_jar 'spark_distributions/spark-3.1.1-bin-hadoop3.2' '3.1.1' '2.12')
