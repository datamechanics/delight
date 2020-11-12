# Delight by Data Mechanics

[Delight](https://www.datamechanics.co/delight) is a free and cross-platform Spark UI replacement with new metrics and visualizations that will delight you!

## Timeline

- June 2020: Project starts with a [blog post](https://www.datamechanics.co/blog-post/building-a-better-spark-ui-data-mechanics-delight) detailing our vision.
- November 2020: First release consisting of a free hosted Spark History Server (no new metrics and visualizations yet).
- January 2021 (expected): Release of the overview screen with new metrics and visualizations.

We hope that the current release (free hosted Spark History Server) will be useful to the Spark developers who cannot easily spin up a SHS on their Spark platform.

## Architecture

Delight consists of an open-sourced [SparkListener](https://mallikarjuna_g.gitbooks.io/spark/content/spark-SparkListener.html) which runs inside your Spark applications and which is very simple to install.
This agent streams Spark event logs to our servers. You can then access the Spark UI for all your Spark applications through our [website](https://www.datamechanics.co/delight).

## Installation

To use Delight:

- Create an account and generate an access token on our [website](https://www.datamechanics.co/delight). To share a single dashboard with your colleagues, you should use your company's Google account on signup.
- Follow the installation instructions below for your platform.

Here are the available instructions:

- [Local run with the `spark-submit` CLI](documentation/local_run.md)
- [Generic instructions for the `spark-submit` CLI](documentation/spark_submit.md)
- [AWS EMR](documentation/aws_emr.md)
- [Google Cloud Dataproc](documentation/dataproc.md)
- [Spark on Kubernetes operator](documentation/spark_operator.md)

## Contact Us

If you have a question, first please read our [FAQ](https://www.datamechanics.co/delight), and [contact us](https://www.datamechanics.co/contact-us) if you don't find your answer. If you want to report a bug or issue a feature request, please use Github issues. Thank you!

## Frequently asked questions

### NoSuchMethodError

I installed Delight and saw the following error in the driver logs. How to solve it?

```
Exception in thread "main" java.lang.NoSuchMethodError: org.apache.spark.internal.Logging.$init$(Lorg/apache/spark/internal/Logging;)V
	at co.datamechanics.delight.DelightListener.<init>(DelightListener.scala:11)
	at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
	at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
```

This probably means that the Scala version of Delight does not match the Scala version of the Spark distribution.

If you specified `co.datamechanics:delight_2.11:latest-SNAPSHOT`, please change to `co.datamechanics:delight_2.12:latest-SNAPSHOT`. And vice versa!

## Configurations

| Config                             | Explanation                                                                                                                                      | Default value    |
| :--------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------- | :--------------- |
| `spark.delight.accessToken.secret` | An access token to authenticate yourself with Data Mechanics Delight. If the access token is missing, the listener will not stream events        | (none)           |
| `spark.delight.appNameOverride`    | The name of the app that will appear in Data Mechanics Delight. This is only useful if your platform does not allow you to set `spark.app.name`. | `spark.app.name` |

### Advanced configurations

We've listed more technical configurations in this section for completeness.
You should not need to change the values of these configurations though, so drop us a line if you do, we'll be interested to know more!

| Config                                                  | Explanation                                                                                                                                                                                                                               | Default value                                   |
| :------------------------------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------- |
| `spark.delight.collector.url`                           | URL of the Data Mechanics Delight collector API                                                                                                                                                                                           | https://api.delight.datamechanics.co/collector/ |
| `spark.delight.buffer.maxNumEvents`                     | The number of Spark events to reach before triggering a call to Data Mechanics Collector API. Special events like job ends also trigger a call.                                                                                           | 1000                                            |
| `spark.delight.payload.maxNumEvents`                    | The maximum number of Spark events to be sent in one call to Data Mechanics Collector API.                                                                                                                                                | 10000                                           |
| `spark.delight.heartbeatIntervalSecs`                   | (Internal config) the interval at which the listener send an heartbeat requests to the API. It allow us to detect if the app was prematurely finished and start the processing ASAP                                                       | 10s                                             |
| `spark.delight.pollingIntervalSecs`                     | (Internal config) the interval at which the object responsible for calling the API checks whether there are new payloads to be sent                                                                                                       | 0.5s                                            |
| `spark.delight.maxPollingIntervalSecs`                  | (Internal config) upon connection error, the polling interval increases exponentially until this value. It returns to its initial value once a call to the API passes through                                                             | 60s                                             |
| `spark.delight.maxWaitOnEndSecs`                        | (Internal config) the time the Spark application waits for remaining payloads to be sent after the event `SparkListenerApplicationEnd`. Not applicable in the case of Databricks                                                          | 10s                                             |
| `spark.delight.waitForPendingPayloadsSleepIntervalSecs` | (Internal config) the interval at which the object responsible for calling the API checks whether there are new remaining to be sent, after the event `SparkListenerApplicationEnd` is received. Not applicable in the case of Databricks | 1s                                              |
