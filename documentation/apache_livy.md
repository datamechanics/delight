# Installation steps when submitting Spark applications through Apache Livy

This document details instructions to install Delight when using [Apache Livy REST API](https://livy.incubator.apache.org/docs/latest/rest-api.html), using the /sessions or the /batches routes.

This document assumes that you have created an account and generated an access token on the [Delight website](https://www.datamechanics.co/delight).

Add the following key-value pairs to the `conf` (Spark configuration properties) field:

```bash
spark.jars.packages=co.datamechanics:delight_<replace-with-your-scala-version-2.11-or-2.12>:latest-SNAPSHOT
spark.jars.repositories=https://oss.sonatype.org/content/repositories/snapshots
spark.delight.accessToken.secret=<your access token>
spark.extraListeners=co.datamechanics.delight.DelightListener
```

A real-world example of submission instrumented with Delight would look like this:

```bash
POST /batches
{
  "file": "/test/spark-examples.jar",
  "className": "org.apache.spark.examples.SparkPi",
  "driverMemory": "1G",
  "driverCores": 1,
  "executorCores": 3,
  "executorMemory": "20G",
  "numExecutors": 2,
  "name": "application-name",
  "conf": {
    "spark.jars.packages": "co.datamechanics:delight_<replace-with-your-scala-version-2.11-or-2.12>:latest-SNAPSHOT",
    "spark.jars.repositories": "https://oss.sonatype.org/content/repositories/snapshots",
    "spark.delight.accessToken.secret": "<your access token>",
    "spark.extraListeners": "co.datamechanics.delight.DelightListener"
  },
  "args": ["1000"]
}
```

> Delight provides information about memory usage for Spark version 3.0.0 and above.
> For this feature to work, you'll need the proc filesystem (`procfs`) and the command `pgrep` available in your runtime.
>
> If you're running Apache Livy on AWS EMR, Google Dataproc, or Databricks, `procfs` and `pgrep` are available. On other systems, you may have to install them. `pgrep` is usually part of the `procps` package on UNIX operating systems.
