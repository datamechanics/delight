package co.datamechanics.delight

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

import org.apache.spark.internal.Logging

object Utils extends Logging {

  def currentTime: Long = System.currentTimeMillis()

  def compressString(inputString: String): Array[Byte] = {
    val input = inputString.getBytes("UTF-8")
    val bos = new ByteArrayOutputStream(input.length)
    val gzip = new GZIPOutputStream(bos)
    gzip.write(input)
    gzip.close()
    val compressed = bos.toByteArray
    bos.close()
    compressed
  }

  def time[R](shouldLogDuration: Boolean, blockName: String)(block: => R): R = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    if(shouldLogDuration) {
      val nanoDuration = t1 - t0
      val durationString = if(nanoDuration >= 1e7) {
        ((t1 - t0) / 1000000) + " ms"
      } else {
        ((t1 - t0) / 1000) + " µs"
      }
      logInfo("Elapsed time in \"" + blockName + "\": " + durationString)
    }
    result
  }
}
