# Installation steps for the Spark on Kubernetes operator

This document details instructions to use Delight with the [Spark on Kubernetes operator](https://github.com/GoogleCloudPlatform/spark-on-k8s-operator).
Here's the official [quick start guide](https://github.com/GoogleCloudPlatform/spark-on-k8s-operator/blob/master/docs/quick-start-guide.md) to install the operator on your Kubernetes cluster.

It assumes that you have created an account and generated an access token on the [Delight website](https://www.datamechanics.co/delight).

To enable Delight in a Spark application launched with the Spark on Kubernetes operator, add the following block to your `sparkapplication` manifest:

```yaml
apiVersion: "sparkoperator.k8s.io/v1beta2"
kind: SparkApplication
# ...
spec:
  deps:
    jars:
      - "https://oss.sonatype.org/content/repositories/snapshots/co/datamechanics/delight_<replace-with-your-scala-version-2.11-or-2.12>/latest-SNAPSHOT/delight_<replace-with-your-scala-version-2.11-or-2.12>-latest-SNAPSHOT.jar"
  sparkConf:
    "spark.delight.accessToken.secret": "<replace-with-your-access-token>"
    "spark.extraListeners": "co.datamechanics.delight.DelightListener"
  #...
```

Do not forget to input the Scala version of your Spark distribution in the placeholders above.
Spark 3.0.0 and above is compatible with Scala `2.12` only; for earlier versions of Spark, please check the Spark distribution in your Docker image.

Here's the [Spark Pi example](https://github.com/GoogleCloudPlatform/spark-on-k8s-operator/blob/master/examples/spark-pi.yaml) from the Spark on Kubernetes operator repository instrumented with Delight:

```yaml
apiVersion: "sparkoperator.k8s.io/v1beta2"
kind: SparkApplication
metadata:
  name: spark-pi
  namespace: default
spec:
  type: Scala
  mode: cluster
  image: "gcr.io/spark-operator/spark:v3.0.0"
  imagePullPolicy: Always
  mainClass: org.apache.spark.examples.SparkPi
  mainApplicationFile: "local:///opt/spark/examples/jars/spark-examples_2.12-3.0.0.jar"
  sparkVersion: "3.0.0"
  restartPolicy:
    type: Never
  deps:
    jars:
      - "https://oss.sonatype.org/content/repositories/snapshots/co/datamechanics/delight_2.12/latest-SNAPSHOT/delight_2.12-latest-SNAPSHOT.jar"
  sparkConf:
    "spark.delight.accessToken.secret": "<replace-with-your-access-token>"
    "spark.extraListeners": "co.datamechanics.delight.DelightListener"
  volumes:
    - name: "test-volume"
      hostPath:
        path: "/tmp"
        type: Directory
  driver:
    cores: 1
    coreLimit: "1200m"
    memory: "512m"
    labels:
      version: 3.0.0
    serviceAccount: spark
    volumeMounts:
      - name: "test-volume"
        mountPath: "/tmp"
  executor:
    cores: 1
    instances: 1
    memory: "512m"
    labels:
      version: 3.0.0
    volumeMounts:
      - name: "test-volume"
        mountPath: "/tmp"
```
