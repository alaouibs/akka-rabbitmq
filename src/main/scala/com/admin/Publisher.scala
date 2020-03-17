package com.admin


import java.nio.file.FileSystems

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.alpakka.amqp.scaladsl.AmqpSink
import akka.stream.alpakka.amqp.{AmqpConnectionUri, AmqpSinkSettings, QueueDeclaration}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.Duration

object Publisher extends App {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()

  // Connection to RabbitMQ
  val queueName = "myqueue"
  val queueDeclaration = QueueDeclaration(queueName, durable = true)
  val uri = "amqp://admin:admin@localhost:5672/myvhost"
  val settings = AmqpSinkSettings(AmqpConnectionUri(uri))
    .withRoutingKey("foobar")
    .withExchange("exchange")
    .withDeclarations(queueDeclaration)
  val amqpSink = AmqpSink.simple(settings)

  val titleLines: Iterator[String] = io.Source.fromFile("src/main/resources/title.basics.tsv", "utf-8").getLines()
  // immutable flow step to split apart our csv into a string array

  val csvHandler = Flow[String].drop(1)
    .map(_.split("\t").toList) // We split the string to retrieve a list of string

  val flow1 = Flow[List[String]].filter(list => list(1) == "movie") // Only titles that are movies are kept
  val flow2 = Flow[List[String]].filter(list => list.last.contains("Comedy")) // Only titles that are comedy movies are kept
  val flow3 = Flow[List[String]].map(list => list.mkString("\t"))
  val flow4 = Flow[String].map(list => ByteString(list)) // We prepare the string to be sent in a broker message

  // @formatter:off
  val g = RunnableGraph.fromGraph(GraphDSL.create(amqpSink) {
    implicit builder =>
      s =>
        import GraphDSL.Implicits._

        // Source
        val A: Outlet[String] = builder.add(Source.fromIterator(() => titleLines)).out

        val B: FlowShape[String, List[String]] = builder.add(csvHandler)
        val C: FlowShape[List[String], List[String]] = builder.add(flow1)
        val D: FlowShape[List[String], List[String]] = builder.add(flow2)
        val E: FlowShape[List[String], String] = builder.add(flow3)
        val F: FlowShape[String, ByteString] = builder.add(flow4)

        // Graph
        A ~> B ~> C ~> D ~> E ~> F ~> s.in
        ClosedShape
  })
  // @formatter:on

  val future = g.run()
  future.onComplete { _ =>
    actorSystem.terminate()
  }
  Await.result(actorSystem.whenTerminated, Duration.Inf)
}
