package app.impl.scala

import org.junit.Test

/**
  * Matches patter allow you to avoid all the verbose if/else structure, using an elegant switch style.
  */
class PatternMatchingFeature {


  @Test def matches(): Unit = {
    println(matchTest(1))
    println(matchTest("test"))
    println(matchTest(2))
  }

  @Test def matchesOnPractice(): Unit = {
    val list = List[Any](1, 2, "test", 5)
    val newList = list.toStream
      .map(i => matchTest(i))
      .toList
    println(newList)
  }

  @Test def matchesOnList(): Unit = {
    val list = List[Any](1, 2, "test", 5)
    list.foreach {
      case 1 => println("one")
      case 2 => println("two")
      case _ => println("?")
    }
  }

  @Test def matchesOnMap(): Unit = {
    Map(1 -> 1, 2 -> "2", 3 -> 3).foreach { entry =>
      entry._1 match {
        case 1 => println(s"Map value:${entry._2.asInstanceOf[Integer] * 100}")
        case 2 => println(s"Map value:${entry._2.asInstanceOf[String].toUpperCase()}")
        case 3 => println(s"Map value:${entry._2.asInstanceOf[Integer] * 100}")
        case _ => println("???")
      }
    }
  }

  /**
    * Get a value from a case we use variable name before @
    */
  @Test
  def getValueFromCase(): Unit ={
    val person = Person("Pablo",37)
    val output = person match {
      case p@Person(_, _) => s"I have $p"
    }
    println(output)
  }

  def matchTest(x: Any): Any = x match {
    case 1 =>
      "one".toUpperCase
    case 2 => 2
    case "test" =>
      "TEST".toLowerCase
    case _ => "many"
  }

  val PARAMS="'(.*)'='(.*)'"
  val PAYLOAD_VALUE_REGEX = s"^Payload $PARAMS".r

  @Test
  def regex(): Unit = {
    val list = List("Not a match", "Payload 'name'='value'", "OXFORD")

    list.foreach {
      case PAYLOAD_VALUE_REGEX(c, c1) => {
        println(c)
        println(c1)
        test("fit")
      }
      case s if s.matches("""OXF.*""") => println("XXX")
      case _ => println("NO MATCHING")
    }
  }

  /**
    * Thanks to pattern match we can define multiple variables.
    */
  @Test
  def defineMultipleValues(): Unit ={
    val Person(myName, myAge) = Person("Pablo",37)
    println(myName)
    println(myAge)
  }



  def test(implicit sentence: String): Unit = {
    println(sentence)
  }

  case class Person(name:String, age:Int)
}



