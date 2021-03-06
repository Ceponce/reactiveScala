package app.impl.scalaz.io

import org.junit.Test
import scalaz.ioeffect.{Fiber, IO, RTS}

import scala.concurrent.duration._

/**
  * Created by pabloperezgarcia aka politrons on 30/04/2018.
  *
  * In this examples you will see that I normally compare with another Reactive library [Rx] that's basically since I´ve been
  * working with that library for years and it's a good reference for me to understand better these operators.
  * If you're interested in learn more about RXJava have fun here!. [https://github.com/politrons/reactive]
  *
  * Monad IO is a monad like [Observable] of Rx, that helps to implement pure Functional programing without side effects.
  * Everything it's typed, and is using an approach similar as the Either where you have a Left or Right type in your
  * output.
  */
class IOMonad extends RTS {


  /**
    * Just like in Rx if we use [now] operator to apply a value to the monad it will be set the value at the moment of the
    * creation of the monad. But if we use [point] it will evaluated the value passed in the IO monad when it
    * is interpreted.
    *
    * To interpret an IO it's exactly the same than when in Rx we subscribe to an Observable. It's the moment when
    * we execute our monad.
    * In order to do it we have to just pass our IO into unsafePerformIO function.
    */
  @Test
  def normalVsDefer(): Unit = {
    var sentence = "Hello World now"
    val deferIO: IO[Void, String] = IO.point(sentence)
    val nowIO: IO[Void, String] = IO.now(sentence)
    sentence = sentence.replace("now", "later")
    println(unsafePerformIO(deferIO))
    println(unsafePerformIO(nowIO))
  }

  @Test
  def impureCode(): Unit = {
    val nanoTime: IO[Void, Long] =
      IO.sync(System.nanoTime())
    println(unsafePerformIO(nanoTime))
  }

  /**
    * Like other monad we can just use map to transform data and flatMap to compose IOs
    */
  @Test
  def happyMapping(): Unit = {
    val sentence: IO[Throwable, String] =
      IO.point("Hello impure world")
        .map(sentence => sentence.replace("impure", "pure"))
        .map(sentence => sentence.toUpperCase())
        .flatMap(sentence => IO.point(sentence.concat("!!!!")))
    println(unsafePerformIO(sentence))
  }

  /**
    * Peek operator receive the current value of the pipeline and the function expect that you output will be another
    * IO but does not really matter since it's ignore. The reason why we need to just return an IO is confusing me,
    * and I think is just a technical limitation that it will be solved. In java peek it's just a Consumer function.
    */
  @Test
  def peekOperator(): Unit = {
    val sentence: IO[Throwable, String] =
      IO.point("Hello impure world")
        .map(sentence => sentence.replace("impure", "pure"))
        .peek(_ => IO.point[Throwable, String]("This value it will be ignore"))
        .flatMap(sentence => IO.point(sentence.concat("!!!!")))
    println(unsafePerformIO(sentence))
  }

  /**
    * CatchAll operator is really handy when you have to treat with unsafe code that might propagate unexpected side effect
    * in your pipeline as Throwable.
    * Since we have this catch in our pipeline whatever not expected effect it will catch and transform in the expected
    * output type of the IO
    */
  @Test
  def catchAllOperator(): Unit = {
    var value: String = null
    val errorSentence = IO.point[Throwable, String](value)
      .flatMap(value => IO.syncThrowable(value.toUpperCase()))
      .catchAll[Throwable](t => IO.now(s"Default value since $t happens"))
      .map(value => value.toUpperCase())

    println(unsafePerformIO(errorSentence))
    value = "Now it should works right?"
    println(unsafePerformIO(errorSentence))
  }

  /**
    * CatchSome operator is really handy when you have to treat with unsafe code that might propagate unexpected side effect
    * in your pipeline as Throwable.
    * Using pattern matching we can decide which type of throwable we want to catch and transform to IO
    * The behaviour is just like Observable.onErrorResumeNext operator of Rx.
    */
  @Test
  def catchSomeOperator(): Unit = {
    var value: String = null
    val errorSentence = IO.point[Throwable, String](value)
      .flatMap(value => IO.syncThrowable(value.toUpperCase())) //This line will make first test fail
      .flatMap(value => IO.syncThrowable(value.substring(30, 56))) //This line will make second test fail
      .catchSome {
      case t: NullPointerException => IO.now[Throwable, String](s"You had a $t")
      case _ => IO.now("What was that?!")
    }
    println(unsafePerformIO(errorSentence))
    value = "ArrayIndexOutOfBoundException"
    println(unsafePerformIO(errorSentence))
    value = "ArrayIndexOutOfBoundException it's not gonna happen now!"
    println(unsafePerformIO(errorSentence))
  }

  /**
    * With fail operator we can create a Monad of Type T that represent the error on your pipeline.
    * Then with  catchAll operator we can recover from that type of business error.
    */
  @Test
  def failOperator(): Unit = {
    val error: IO[Throwable, String] =
      IO.fail(CustomError("This is my custom error"))
        .catchAll[Throwable](t => IO.now(s"Default value since $t happens"))
    println(unsafePerformIO(error))
  }

  /**
    * In case of IO.fail we can use leftMap operator to map the Left side of the monad.
    * In case we can control a System throwable into business error throwable
    */
  @Test
  def leftMapOperator(): Unit = {
    val sentence: IO[CustomError, String] =
      IO.fail(new NullPointerException)
        .leftMap[Throwable](error => CustomError(s"Wow un error just happen $error"))
        .catchAll[CustomError](t => IO.now(s"Default value since $t happens"))
    println(unsafePerformIO(sentence))
  }

  /**
    * Again, we have to realize that Pure FP in IO communications cannot exist, is impure by design, the network might fail,
    * The server you call might not be available and so on. So one more time we have to assume that some impure code
    * might fail.
    * Here Retry operator will take of retry the operation until he achieve to receive the type that IO expect for the output.
    *
    * It will retry the operator forever until achieve the result that expect
    */
  @Test
  def retryOperator(): Unit = {
    val sentence: IO[Throwable, String] =
      IO.point(getSentence(0.0001))
        .flatMap(value => {
          println(s"Current value: $value")
          IO.syncThrowable(value.toUpperCase())
        })
        .retry
    println(unsafePerformIO(sentence))
  }

  /**
    * This operator works just like retry, with a backoff time between every retry with a multiplication factor
    * against the before time of retry specify in the duration.
    * Also we provide a first int value, that specify the maximum retries attempts.
    */
  @Test
  def retryBackoffOperator(): Unit = {
  var startTime: Long = System.currentTimeMillis()
    val sentence: IO[Throwable, String] =
      IO.point(getSentence(0.09))
        .flatMap(value => {
          println(s"Current value: $value after wait ${System.currentTimeMillis() - startTime}")
          startTime = System.currentTimeMillis()
          IO.syncThrowable(value.toUpperCase())
        })
        .retryBackoff(10, 2, 10 millis)
    println(unsafePerformIO(sentence))
  }

  def getSentence(perc: Double): String = {
    if (math.random < perc) "Hi pure functional world" else null
  }

  /**
    * Fiber is like Scala Future, the execution of the process it will executed in another thread.
    * Here the syntax it's quite clear, when we want to start the execution in a new Thread we use [fork]
    * operator. At that moment IO create a new output type as Fiber[L,R]
    *
    * Just like with futures after we run the execution of the IO function we will have to wait until the other
    * Thread finish. Here we just add a silly Sleep to wait for the resolution.
    */
  @Test
  def fiberFeature(): Unit = {
    println(s"Before ${Thread.currentThread().getName}")
    val ioFuture: IO[Throwable, Fiber[Throwable, Unit]] = IO.point[Throwable, String]("Hello async IO world")
      .map(sentence => {
        println(s"$sentence ${Thread.currentThread().getName}")
      }).delay(1 second)
      .fork[Throwable] //This operator make the execution of the function run in another thread.

    println(s"After: ${Thread.currentThread().getName}")
    unsafePerformIO(ioFuture)
    Thread.sleep(2000)
  }

  /**
    * Using the join operator we can join the thread local values from one thread into the main returning
    * the Fiber type R form Fiber[L,R] to IO[L,R]
    */
  @Test
  def fiberAwait(): Unit = {
    println(s"Before ${Thread.currentThread().getName}")
    val ioFuture: IO[Throwable, Fiber[Throwable, String]] = IO.point[Throwable, String]("Hello async IO world")
      .delay(1 seconds)
      .map(sentence => sentence.toUpperCase())
      .fork[Throwable]

    println(s"After: ${Thread.currentThread().getName}")
    val sentence = ioFuture.flatMap(fiber => fiber.join)
    println(unsafePerformIO(sentence))
  }

  /**
    * Same example that before but with more sugar.
    */
  @Test
  def fiberAwaitSugar(): Unit = {
    println(s"Before ${Thread.currentThread().getName}")
    val ioFuture: IO[Throwable, String] = IO.point[Throwable, String]("Hello async IO world")
      .delay(1 seconds)
      .map(sentence => sentence.toUpperCase())

    val result = for {
      fiber <- ioFuture.fork
      value <- fiber.join
    } yield value

    println(s"After: ${Thread.currentThread().getName}")
    println(unsafePerformIO(result))
  }

  /**
    * IO unfortunately has no fancy operators as zip, but maybe I'm one of the few guys that I normally never use
    * Zip but just flatMap for composition of Futures. Anyway you will see Par operator later ;)
    * So here with Fibers we can do pretty much the same.
    * In this example we use some sugar to make the composition of the Fibers created by the Fork.
    */
  @Test
  def compositionOfFibersWithSugar(): Unit = {
    println(s"Before ${Thread.currentThread().getName}")

    def composition: IO[Throwable, String] = for {
      fiber <- createIO("Business logic 1").fork
      fiber1 <- createIO("Business logic 2").fork
      v2 <- fiber1.join
      v1 <- fiber.join
    } yield v1 + v2

    println(s"After: ${Thread.currentThread().getName}")
    println(unsafePerformIO(composition))
  }

  /**
    * And Here if you're a Hardcore FP same example without sugar syntax
    */
  @Test
  def compositionOfFibersNotSugar(): Unit = {
    println(s"Before ${Thread.currentThread().getName}")
    val composition: IO[Throwable, String] = createIO("Business logic 1").fork
      .flatMap(fiber => createIO("Business logic 2").fork
        .flatMap(fiber1 => fiber1.join
          .flatMap(v2 => fiber.join
            .map(v1 => v1 + v2))))
    println(s"After: ${Thread.currentThread().getName}")
    println(unsafePerformIO(composition))
  }

  private def createIO(sentence: String): IO[Throwable, String] = {
    IO.point[Throwable, String](sentence)
      .map(sentence => s" $sentence ${Thread.currentThread().getName}".toUpperCase())
      .delay(1 second)
  }

  /**
    * Race operator is a really interesting feature that IO monad introduce. It allow us to start two
    * process in parallel in two threads and once of the Threads has finish the process the [race]
    * operator take care of close and clean the unfinished process in the thread and return the finished
    * value.
    * This can be a powerful tool to get for instance information from two sources.
    */
  @Test
  def raceFeature(): Unit = {
    val car1: IO[Throwable, String] = createCar("Porsche")
    val car2: IO[Throwable, String] = createCar("Lotus")
    val winner = car1.race(car2)
    println(unsafePerformIO(winner))
  }

  /**
    * Here we show same example but in a Daytona race with more cars.
    */
  @Test
  def daytonaRace(): Unit = {
    val car1: IO[Throwable, String] = createCar("Porsche")
    val car2: IO[Throwable, String] = createCar("Lotus")
    val car3: IO[Throwable, String] = createCar("Maserati")
    val car4: IO[Throwable, String] = createCar("Ferrari")
    val car5: IO[Throwable, String] = createCar("Honda")

    val winner = car1.race(car2).race(car3).race(car4).race(car5)
    println(unsafePerformIO(winner))
  }

  /**
    * Par operator run in parallel the N process and return tuple, which can contain tuple as value.
    * Is exactly the same result type that [Future.zip] of scala .
    */
  @Test
  def parallelismFeature(): Unit = {
    val car1: IO[Throwable, String] = createCar("Porsche")
    val car2: IO[Throwable, String] = createCar("Lotus")
    val winner = car1.par(car2)
    val tuple = unsafePerformIO(winner)
    println(tuple._1)
    println(tuple._2)

  }


  private def createCar(car: String): IO[Throwable, String] = IO.point(car)
    .map(car => {
      Thread.sleep((Math.random * 1500).toInt)
      println(s"$car running in ${Thread.currentThread().getName}")
      s" $car win!"
    })

  case class CustomError(message: String) extends Throwable


}

