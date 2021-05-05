package co.datamechanics.delight.metrics

import java.io._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

import org.apache.spark.SparkException
import org.apache.spark.internal.Logging
import org.apache.spark.UtilsProxy.utils

import co.datamechanics.delight.dto.ProcfsMetrics

// This is a port of ProcfsMetricsGetter from org.apache.spark.executor
// This will allow us to monitor memory on the driver
class ProcfsMetricsGetter(procfsDir: String = "/proc/") extends Logging {
  private val procfsStatFile = "stat"
  private val testing = utils.isTesting
  private val pageSize = computePageSize()
  private var isAvailable: Boolean = isProcfsAvailable
  private val pid = computePid()

  private lazy val isProcfsAvailable: Boolean = {
    if (testing) {
      true
    } else {
      val procDirExists = Try(Files.exists(Paths.get(procfsDir))).recover {
        case ioe: IOException =>
          logWarning("Exception checking for procfs dir", ioe)
          false
      }
      procDirExists.get
    }
  }

  private def computePid(): Int = {
    if (!isAvailable || testing) {
      return -1;
    }
    try {
      // This can be simplified in java9:
      // https://docs.oracle.com/javase/9/docs/api/java/lang/ProcessHandle.html
      val cmd = Array("bash", "-c", "echo $PPID")
      val out = utils.executeAndGetOutput(cmd)
      Integer.parseInt(out.split("\n")(0))
    } catch {
      case e: SparkException =>
        logWarning(
          "Exception when trying to compute process tree." +
            " As a result reporting of ProcessTree metrics is stopped",
          e
        )
        isAvailable = false
        -1
    }
  }

  private def computePageSize(): Long = {
    if (testing) {
      return 4096;
    }
    try {
      val cmd = Array("getconf", "PAGESIZE")
      val out = utils.executeAndGetOutput(cmd)
      Integer.parseInt(out.split("\n")(0))
    } catch {
      case e: Exception =>
        logWarning(
          "Exception when trying to compute pagesize, as a" +
            " result reporting of ProcessTree metrics is stopped"
        )
        isAvailable = false
        0
    }
  }

  private def computeProcessTree(): Set[Int] = {
    if (!isAvailable || testing) {
      return Set()
    }
    var ptree: Set[Int] = Set()
    ptree += pid
    val queue = mutable.Queue.empty[Int]
    queue += pid
    while (!queue.isEmpty) {
      val p = queue.dequeue()
      val c = getChildPids(p)
      if (!c.isEmpty) {
        queue ++= c
        ptree ++= c.toSet
      }
    }
    ptree
  }

  private def getChildPids(pid: Int): ArrayBuffer[Int] = {
    try {
      val builder = new ProcessBuilder("pgrep", "-P", pid.toString)
      val process = builder.start()
      val childPidsInInt = mutable.ArrayBuffer.empty[Int]
      def appendChildPid(s: String): Unit = {
        if (s != "") {
          logTrace("Found a child pid:" + s)
          childPidsInInt += Integer.parseInt(s)
        }
      }
      val stdoutThread = utils.processStreamByLine(
        "read stdout for pgrep",
        process.getInputStream,
        appendChildPid
      )
      val errorStringBuilder = new StringBuilder()
      val stdErrThread = utils.processStreamByLine(
        "stderr for pgrep",
        process.getErrorStream,
        line => errorStringBuilder.append(line)
      )
      val exitCode = process.waitFor()
      stdoutThread.join()
      stdErrThread.join()
      val errorString = errorStringBuilder.toString()
      // pgrep will have exit code of 1 if there are more than one child process
      // and it will have a exit code of 2 if there is no child process
      if (exitCode != 0 && exitCode > 2) {
        val cmd = builder.command().toArray.mkString(" ")
        logWarning(
          s"Process $cmd exited with code $exitCode and stderr: $errorString"
        )
        throw new SparkException(s"Process $cmd exited with code $exitCode")
      }
      childPidsInInt
    } catch {
      case e: Exception =>
        logWarning(
          "Exception when trying to compute process tree." +
            " As a result reporting of ProcessTree metrics is stopped.",
          e
        )
        isAvailable = false
        mutable.ArrayBuffer.empty[Int]
    }
  }

  private def addProcfsMetricsFromOneProcess(
      allMetrics: ProcfsMetrics,
      pid: Int
  ): ProcfsMetrics = {

    // The computation of RSS and Vmem are based on proc(5):
    // http://man7.org/linux/man-pages/man5/proc.5.html
    try {
      val pidDir = new File(procfsDir, pid.toString)
      def openReader(): BufferedReader = {
        val f = new File(new File(procfsDir, pid.toString), procfsStatFile)
        new BufferedReader(
          new InputStreamReader(new FileInputStream(f), UTF_8)
        )
      }
      utils.tryWithResource(openReader) { in =>
        val procInfo = in.readLine
        val procInfoSplit = procInfo.split(" ")
        val vmem = procInfoSplit(22).toLong
        val rssMem = procInfoSplit(23).toLong * pageSize
        if (procInfoSplit(1).toLowerCase(Locale.US).contains("java")) {
          allMetrics.copy(
            jvmVmem = allMetrics.jvmVmem + vmem,
            jvmRSS = allMetrics.jvmRSS + (rssMem)
          )
        } else if (procInfoSplit(1).toLowerCase(Locale.US).contains("python")) {
          allMetrics.copy(
            pythonVmem = allMetrics.pythonVmem + vmem,
            pythonRSS = allMetrics.pythonRSS + (rssMem)
          )
        } else {
          allMetrics.copy(
            otherVmem = allMetrics.otherVmem + vmem,
            otherRSS = allMetrics.otherRSS + (rssMem)
          )
        }
      }
    } catch {
      case f: IOException =>
        logWarning(
          "There was a problem with reading" +
            " the stat file of the process. ",
          f
        )
        throw f
    }
  }

  def computeAllMetrics(): ProcfsMetrics = {
    if (!isAvailable) {
      return ProcfsMetrics(0, 0, 0, 0, 0, 0)
    }
    val pids = computeProcessTree
    var allMetrics = ProcfsMetrics(0, 0, 0, 0, 0, 0)
    for (p <- pids) {
      try {
        allMetrics = addProcfsMetricsFromOneProcess(allMetrics, p)
        // if we had an error getting any of the metrics, we don't want to
        // report partial metrics, as that would be misleading.
        if (!isAvailable) {
          return ProcfsMetrics(0, 0, 0, 0, 0, 0)
        }
      } catch {
        case _: IOException =>
          return ProcfsMetrics(0, 0, 0, 0, 0, 0)
      }
    }
    allMetrics
  }
}

object ProcfsMetricsGetter {
  final val procfsMetricsGetter = new ProcfsMetricsGetter

  def get(): ProcfsMetricsGetter = {
    procfsMetricsGetter
  }
}
