# Installation steps for Databricks

This document details instructions to install Delight on Databricks.

It assumes that you have created an account and generated an access token on the [Delight website](https://www.datamechanics.co/delight).

The easiest way to enable Delight on Databricks is to define an [init script](https://docs.databricks.com/clusters/init-scripts.html) that configures Delight on cluster startup.

## Global init script or cluster-scoped init script?

Databricks offers two types of [init scripts](https://docs.databricks.com/clusters/init-scripts.html):
* [cluster-scoped init scripts](https://docs.databricks.com/clusters/init-scripts.html#cluster-scoped-init-scripts): a cluster-scoped init script is tied to a cluster and will only execute when this cluster starts
* [global init scripts](https://docs.databricks.com/clusters/init-scripts.html#global-init-scripts-new): a global init script is shared by all clusters. It executes on all clusters in your Databricks account

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
          https://oss.sonatype.org/content/repositories/snapshots/co/datamechanics/delight_$SCALA_VERSION/latest-SNAPSHOT/delight_$SCALA_VERSION-latest-SNAPSHOT.jar

        cat > $SPARK_DEFAULTS_FILE <<- EOF
        [driver] {
          "spark.extraListeners"             = "com.databricks.backend.daemon.driver.DBCEventLoggingListener,co.datamechanics.delight.DelightListener"
          "spark.delight.accessToken.secret" = "$ACCESS_TOKEN"
        }
EOF
fi
```
