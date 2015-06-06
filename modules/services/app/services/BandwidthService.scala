package services

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import helpers._
import models.cfs.Block._
import org.joda.time.DateTime
import play.api.libs.iteratee.Enumeratee.CheckDone
import play.api.libs.iteratee._

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

/**
 * @author zepeng.li@gmail.com
 */
case class BandwidthService(
  basicPlayApi: BasicPlayApi,
  actorSystem: ActorSystem
) extends BasicPlayComponents {

  implicit private val executor = actorSystem.dispatchers.lookup("traffic-shaper-context")

  import BandwidthService._

  private object Config extends CanonicalNamed with AppConfig {

    override val basicName = "bandwidth"

    lazy val max = config.getBytes("max").map(_.toInt).getOrElse(5 MBps)
    lazy val min = config.getBytes("min").map(_.toInt).getOrElse(200 KBps)
  }

  object LimitTo {

    import Config._

    def apply(rate: Int = 1 MBps): Enumeratee[BLK, BLK] = limitTo(
      if (rate < min) min
      else if (rate > max) max
      else rate
    )

  }

  object UnLimited {

    def apply(): Enumeratee[BLK, BLK] = Enumeratee.passAlong
  }

  private def limitTo(rate: Int)(
    implicit ec: ExecutionContext
  ): Enumeratee[BLK, BLK] = new CheckDone[BLK, BLK] {

    def step[B](remaining: Int, start: Long)(
      k: K[BLK, B]
    ): K[BLK, Iteratee[BLK, B]] = {

      case in@Input.El(_) if remaining <= 1 =>
        new CheckDone[BLK, BLK] {
          def continue[A](k: K[BLK, A]) = {
            val spent = now - start
            val cont = Cont(step(rate / 4, now)(k))
            Iteratee.flatten {
              if (spent > 500) Future.successful(cont)
              else timeout(cont, 500 - spent, MILLISECONDS)(ec)
            }
          }
        } &> k(in)

      case in@Input.El(block) if remaining > 1 =>
        new CheckDone[BLK, BLK] {
          def continue[A](k: K[BLK, A]) =
            Cont(step(remaining - block.size, start)(k))
        } &> k(in)

      case Input.Empty if remaining > 0 =>
        new CheckDone[BLK, BLK] {
          def continue[A](k: K[BLK, A]) =
            Cont(step(remaining, start)(k))
        } &> k(Input.Empty)

      case Input.EOF => Done(Cont(k), Input.EOF)

      case in => Done(Cont(k), in)
    }

    def continue[A](k: K[BLK, A]): Iteratee[BLK, Iteratee[BLK, A]] =
      Cont(step(rate / 4, now)(k))
  }

  private def now = DateTime.now.getMillis

  private def timeout[A](
    message: => A,
    duration: Long,
    unit: TimeUnit = TimeUnit.MILLISECONDS
  )(
    implicit ec: ExecutionContext
  ): Future[A] = {
    val p = Promise[A]()
    actorSystem.scheduler.scheduleOnce(FiniteDuration(duration, unit)){
      p.complete(Try(message))
    }
    p.future
  }
}

object BandwidthService {

  implicit class Int2Rate(val i: Int) extends AnyVal {

    def KBps: Int = i * 1024

    def MBps: Int = i * 1024 * 1024
  }

  implicit class Double2Rate(val d: Double) extends AnyVal {

    def KBps: Int = (d * 1024).toInt

    def MBps: Int = (d * 1024 * 1024).toInt
  }

}