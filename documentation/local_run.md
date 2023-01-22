# Tutorial: run a local Spark app with Delight

You can try Delight with a local run on your machine!
[Download a Spark distribution](https://spark.apache.org/downloads.html) and run an instrumented Spark submit from the root folder.

To activate Delight, you'll need to add the following options to your `spark-submit` call:

```bash
--packages io.montara.lucia:sparklistener_<replace-with-your-scala-version-2.11-or-2.12>:latest-SNAPSHOT
--repositories https://oss.sonatype.org/content/repositories/snapshots
--conf spark.extraListeners=io.montara.lucia.sparklistener.LuciaSparkListener
```

Here's a working example for Spark 3.0.1:

```bash
# From the root folder of the Spark 3.0.1 distribution
./bin/spark-submit \
  --class org.apache.spark.examples.SparkPi \
  --master 'local[2]' \
  --packages io.montara.lucia:sparklistener_2.12:latest-SNAPSHOT \
  --repositories https://oss.sonatype.org/content/repositories/snapshots \
  --conf spark.extraListeners=io.montara.lucia.sparklistener.LuciaSparkListener \
  examples/jars/spark-examples_2.12-3.0.1.jar \
  100
```

And a working example for Spark 2.4.7:

```bash
# From the root folder of the Spark 2.4.7 distribution
./bin/spark-submit \
  --class org.apache.spark.examples.SparkPi \
  --master 'local[2]' \
  --packages io.montara.lucia:sparklistener_2.11:latest-SNAPSHOT \
  --repositories https://oss.sonatype.org/content/repositories/snapshots \
  --conf spark.extraListeners=io.montara.lucia.sparklistener.LuciaSparkListener \
  examples/jars/spark-examples_2.11-2.4.7.jar \
  100
```

> Delight provides information about memory usage for Spark version 3.0.0 and above.
> For this feature to work, you'll need the proc filesystem (`procfs`) and the command `pgrep` available in your runtime.
>
> In Debian-based systems for example, `pgrep` is available as part of the `procps` package that you can install with `apt-get install procps`.
>
> Note that `procfs` is not available in OS X.
