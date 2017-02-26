/*
 * btc-hotspot
 * Copyright (C) 2016  Andrea Raspitzu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package watchdog

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import com.typesafe.scalalogging.slf4j.LazyLogging
import commons.Helpers.FutureOption
import iptables.IpTablesInterface
import protocol.domain.Session
import scala.concurrent.duration._
import commons.AppExecutionContextRegistry.context._

trait StopWatch extends LazyLogging {
  
  type StopWatchDependency = {
      val ipTablesService:IpTablesInterface
  }
  
  val dependencies: StopWatchDependency
  val session: Session
  val duration: Long
  
  def start(onLimitReach: => Unit):FutureOption[Unit]
  
  def stop():FutureOption[Unit]
  
  def remainingUnits():Long
  
  def isPending():Boolean
  
}

class TimebasedStopWatch(val dependencies: {
  val ipTablesService:IpTablesInterface
  val scheduler: SchedulerImpl
},val session: Session, val duration: Long) extends StopWatch {
  import dependencies._
  
  
  override def start(onLimitReach: => Unit): FutureOption[Unit] = {
    logger.warn("STARTING THE STOPWATCH")
    ipTablesService.enableClient(session.clientMac) map { ipTablesOut =>
      logger.warn("SCHEDULING TIMEBASED_STOPWATCH")
      scheduler.schedule(session.id, duration millisecond){
        scheduler.remove(session.id)
        this.stop()
        onLimitReach
      }
    }
  }
  
  override def stop(): FutureOption[Unit] = {
    ipTablesService.disableClient(session.clientMac) map { ipTablesOut =>
      // abort scheduled task
      if (isPending) {
        logger.info(s"Aborting scheduled task for session ${session.id}")
        scheduler.cancel(session.id)
      }
    }
  }
  
  override def remainingUnits(): Long = {
    scheduler.scheduledAt(session.id) match {
      case Some(scheduledAt) => ChronoUnit.MILLIS.between(LocalDateTime.now, scheduledAt)
      case None => throw new IllegalArgumentException(s"Could not find schedule for ${session.id}")
    }
  }
  
  override def isPending() = scheduler.isScheduled(session.id)
  
}
//class DatabasedStopWatch extends StopWatch