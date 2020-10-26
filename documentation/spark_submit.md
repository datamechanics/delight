# Installation steps for the `spark-submit` CLI

This document details instructions to install Delight from the [`spark-submit` CLI](https://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit).
This is useful if you run Spark applications on your platform directly with the `spark-submit` CLI.

This document assumes that you have created an account and generated an access token on the [Delight website](https://www.datamechanics.co/delight).

Add the following options to your `spark-submit` call:

```bash
--packages co.datamechanics:delight_<replace-with-your-scala-version-2.11-or-2.12>:latest-SNAPSHOT
--repositories https://oss.sonatype.org/content/repositories/snapshots
--conf spark.delight.accessToken.secret=<replace-with-your-access-token>
--conf spark.extraListeners=co.datamechanics.delight.DelightListener
```

A real-world example of submission instrumented with Delight would look like this:

```bash
# From the root folder of a Spark distribution
./bin/spark-submit \
  --class org.apache.spark.examples.SparkPi \
  --master yarn \
  --packages co.datamechanics:delight_<replace-with-your-scala-version-2.11-or-2.12>:latest-SNAPSHOT \
  --repositories https://oss.sonatype.org/content/repositories/snapshots \
  --conf spark.delight.accessToken.secret=<replace-with-your-access-token> \
  --conf spark.extraListeners=co.datamechanics.delight.DelightListener \
  --deploy-mode cluster \
  --executor-memory 20G \
  --num-executors 50 \
  /path/to/examples.jar \
  1000
```
