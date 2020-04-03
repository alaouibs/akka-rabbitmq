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
    .map(_.split("\t").toList).filter(list => list(1) == "movie").filter(list => list.last.contains("Comedy")).map(list => list.mkString("\t")).map(list => ByteString(list))

  // @formatter:off
  val g = RunnableGraph.fromGraph(GraphDSL.create(amqpSink) {
    implicit builder =>
      s =>
        import GraphDSL.Implicits._

        // Source
        val A: Outlet[String] = builder.add(Source.fromIterator(() => titleLines)).out

        val B: FlowShape[String, ByteString] = builder.add(csvHandler)

        // Graph
        A ~> B ~> s.in
        ClosedShape
  })
  // @formatter:on

  val future = g.run()
  future.onComplete { _ =>
    actorSystem.terminate()
  }
  Await.result(actorSystem.whenTerminated, Duration.Inf)
}
