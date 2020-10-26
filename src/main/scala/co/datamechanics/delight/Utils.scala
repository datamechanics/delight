package co.datamechanics.delight

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

object Utils {

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
}
