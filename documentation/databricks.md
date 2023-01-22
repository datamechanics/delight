# Installation steps for Databricks

This document details instructions to install Delight on Databricks.

It assumes that you have created an account and generated an access token on the [Delight website](https://www.datamechanics.co/delight).

The easiest way to enable Delight on Databricks is to define an [init script](https://docs.databricks.com/clusters/init-scripts.html) that configures Delight on cluster startup.

## Global init script or cluster-scoped init script?

Databricks offers two types of [init scripts](https://docs.databricks.com/clusters/init-scripts.html):

- [cluster-scoped init scripts](https://docs.databricks.com/clusters/init-scripts.html#cluster-scoped-init-scripts): a cluster-scoped init script is tied to a cluster and will only execute when this cluster starts
- [global init scripts](https://docs.databricks.com/clusters/init-scripts.html#global-init-scripts-new): a global init script is shared by all clusters. It executes on all clusters in your Databricks account

We recommend to use a global init script when possible. This way, Delight will be accessible for all your applications without further work.

⚠ Do not use a global init script if you have clusters with different Scala versions in your Databricks account. ⚠️

## Installation

Install the following init script as a [cluster-scoped init script](https://docs.databricks.com/clusters/init-scripts.html#cluster-scoped-init-scripts) or a [global init script](https://docs.databricks.com/clusters/init-scripts.html#global-init-scripts-new). Don't forget to fill out the placeholders!

```bash
#!/bin/bash

SCALA_VERSION="<replace-with-your-scala-version-2.11-or-2.12>"
ACCESS_TOKEN="<replace-with-your-access-token>"

cat > /databricks/spark/dbconf/java/extra.security <<- EOF
# This file has been modified to support Let's Encrypt certificates for the use of Delight (GCM not disabled)
jdk.tls.disabledAlgorithms=SSLv3, RC4, DES, MD5withRSA, DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL
EOF

SPARK_DEFAULTS_FILE="/databricks/driver/conf/00-custom-spark-driver-defaults.conf"

if [[ $DB_IS_DRIVER = "TRUE" ]]; then
        wget --quiet \
          -O /mnt/driver-daemon/jars/delight.jar \
          https://oss.sonatype.org/content/repositories/snapshots/co/datamechanics/sparklistener_$SCALA_VERSION/latest-SNAPSHOT/sparklistener_$SCALA_VERSION-latest-SNAPSHOT.jar

        cat > $SPARK_DEFAULTS_FILE <<- EOF
        [driver] {
          "spark.extraListeners"             = "com.databricks.backend.daemon.driver.DBCEventLoggingListener,io.montara.lucia.sparklistener.LuciaSparkListener"
          "spark.lucia.sparklistener.accessToken.secret" = "$ACCESS_TOKEN"
        }
EOF
fi
```

## Note: Known Issue for Long-Running Clusters

While your cluster is running, the metrics collected by Delight are streamed to our backend, but the statistics and visualizations provided by Delight are not available.
When your cluster is terminated, we parse all the collected metrics and then make the statistics and visualizations available to you.

Unfortunately, this parsing step is currently a bottleneck, and so Delight is not capable of analyzing the metrics for very long-running clusters.

As a rule of thumb, if you attach Delight to ephemeral Jobs clusters (which may run for a few hours at most), then Delight will work fine.
But if you attach Delight to long-running interactive clusters (which may stay up without being restarted for multiple days), then the parsing will fail and you will not have access to Delight.

We will be working to avoid this limitation in future releases.
