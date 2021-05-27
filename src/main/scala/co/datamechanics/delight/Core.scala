package co.datamechanics.delight

import scala.concurrent.duration.FiniteDuration

class Core {}

object Core {
  def startRepeatThread(
      interval: FiniteDuration
  )(action: => Unit): Thread = {
    val thread = new Thread {
      override def run() {
        while (true) {
          val start = currentTime
          val _ = action
          val end = currentTime
          Thread.sleep(math.max(interval.toMillis - (end - start), 0))
        }
      }
    }
    thread.start()
    thread
  }
}
