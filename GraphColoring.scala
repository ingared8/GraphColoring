package com.datascience.waze.spark.streetGraph.lineGraph

import com.datascience.waze.common.Point
import com.datascience.waze.spark.streetGraph.lineGraph.GraphReductionForWaze.{EdgeAttr, EdgeNodeMsg, EdgePass, VertexAttr}
import com.datascience.waze.spark.streetGraph.roadParser.{GeoJsonParser, GraphGenerator}
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.apache.spark.sql.functions._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.graphx.{Graph, VertexRDD, Edge => GXEdge}
import org.apache.spark.sql.types.{IntegerType, LongType}
import org.graphframes.GraphFrame

/**
  * Created by greddy on 8/25/16.
  */
object KMinColoring {

  /*

  In graph theory, graph coloring or vertex/node coloring or k-coloring is an assignment of colors to graph
  vertices/nodes such that no two vertices which are connected should share the same color.
  One can define graph coloring as

  Given an undirected graph G = (V, E), a k-color coloring assigns a color
        color(v) ∈ {1, 2, ..., k} to each node v ∈ V
        such that for ∀(u, v) ∈ E, color(u) != color(v)

  Bounds on chromatic number  χ(G) = minG(k)

   1) 1-coloring is possible if and only if G is edgeless
   2) 2-coloring is possible if and only if G is a bipartite graph.

   In general, a proven greedy coloring shows that every graph can be colored with
   one more color than the maximum vertex degree ∆(G)

   χ(G) <= maximum vertex degree + 1

   χ(G) <=  ∆(G) + 1

   In this algorithm we try to solve graph coloring by considering

    k = ∆(G) + 1

    ∆(G) is represented as ∆ for simplicity.

  *******************************************
  A simple naive algorithm  for ColorReduction
  *******************************************

  function ColorReduction(G = (V, E), ∆)
    n = |V |
    for v = Range(n) do
      color(v) = v
    end for

    for v = ∆ + 2, n do
      color(v) = min({1, ..., ∆ + 1} \ {cu|(u, v) ∈ E})
    end for

  end function

  The line
      color(v) = min({1, ..., ∆+1 } \ {cu|(u, v) ∈ E})
  indicates that a node should choose a min value from (1,...,∆ + 1) which is not taken by its neighbors.

   For example, if my neighbors are colored (1,2,4,5) , then the node will choose its color as 3.

  Source::   https://en.wikipedia.org/wiki/Graph_coloring
  */

  /**
    * @param srcId id of the vertex/node
    * @param color  initial color of the node.
    */
  case class VertexAttr(srcId: Long, color: Long)

  /**
    *
    * @param srcId srcId of the edge
    * @param dstId dstId of the edge
    * @param dist distance of the edge
    */
  case class EdgeAttr(srcId: Long, dstId: Long, dist: Double)

  /**
    * Msg that is passed from one node to its neighbors, to indicate colors.
    *
    * Note: This works when number of colors (k) is less than 32  , as int can only contain
    * 32 bits. Since for a graph like, k is  always bounded by its max degree + 1.
    *
    * For a directed street network this could not exceed 10 or (would be in the range of 2-10).
    *
    * For graph with large k , a bit array could be used.
    *
    * @param value
    */
  case class Msg(value:Int)

  /**
    * Aggregator function
    *
    * Each Message with value(int) will keep track of the
    * its neighbors colors (integers) by setting the corresponding bits.
    *
    * For Example
    *     A node with neighboring colors as 1,2,3,5 will have a value of  000101110
    *     A node with neighboring colors as 0,2,5   will have a value of  000100101
    *
    *     All Neighbors , Bitwise or would be
    *              000101110 |
    *              000100101
    *         =    000101111
    *
    *   Indicating the neighboring colors will be (0,1,2,3,5)
    *
    *
    * This reduction function will save all the neighbors colors by applying a bitwise or
    * operation to all the messages it receives from its neighbors.
    *
    * @param msg1 Int
    * @param msg2 Int
    * @return A new Msg generated by applying bitwise or operation to the msg values.
    */
  def chooseColorMsg(msg1: Msg, msg2: Msg): Msg = {
    Msg(msg1.value | msg2.value)
  }

  /**
    * The msg will contain neighboring color as bits set in its value.
    * We would like to find the minimum position of the bit which is set to 0 (color not taken)
    *
    * For example , lets assume the value of the msg is 00101011011
    * We would like to return 3, as position of min zero bit 00101011[0]11
    *
    * This is done using the following steps
    *
    *  a) Negate the number (ones and zeros will interchange)
    *  b) x & ~(x-1) will just set all bits to zero except the lowest set bit.
    *  c) Position of the this set bit -1 is the desired position.
    *
    * @param msg Aggregation of all messages received from its neighbors
    * @return min position of the zero bit present in the value of message
    */

  def getMinColor(msg: Msg): Long = {

    // Bitwise negation
    val negatedValue = ~msg.value

    // Position of Least significant bit set in the graph
    var position = negatedValue & ~(negatedValue-1)
    var count = 0
    while(position > 0){
        position >>= 1
        count += 1
    }
    count.toLong - 1
  }

  /**
    * This is highly fast compared to naive but works only on a connected graph.
    * If the graph is disconnected , it should be made sure to initialize atleast one
    * value in each connected component to  be initialized with  a value in range of
    * (1, k+1)
    *
    * This works on the principle of the principle of breadth first expansion.
    *
    *
    ********************************************
      A Fast algorithm  for ColorReduction
    *******************************************

    function ColorReductionFast(G = (V, E), ∆, maxIter)
      n = |V |
      for v = Range(n) do
        color(v) = v
      end for

    COLORED = {node | color(node) <= ( ∆ +1 ) }
    NOT_COLORED = {node | color(node) > ( ∆ +1 ) }


    for e ∈ (u,v) | u ∈ COLORED && v ∈ NOT_COLORED {
        c(v) = min({1, ..., ∆ + 1} \ {cu|(u, v) ∈ E})
    }

    (or )

    for iter = 1 , maxIter do
        // updates colors of the neighbors of nodes whose colors are are already labelled.
        color(v) = min({1, ..., ∆ + 1} \ {cu|(u, v) ∈ E}) such that color(u) < ∆ +1  && color (v) > (∆ + 1)
      end for
    end function
    *
    *
    * @param g GraphFrame ( needs to be a directed graph)
    * @param k Max Degree of the Graph
    * @return a GraphFrame whose vertices have a new column called "finalColor"
    */
  def colorGraphReductionFastest(g: GraphFrame, k: Int, maxIter:Int = 30): GraphFrame = {

    val n = g.vertices.count().toInt
    //println("Count ", n)
    val gx0 = g.toGraphX

    // Schema maps for extracting attributes
    // Edges should have an attribute called color
    val vColsMap = g.vertexColumnMap
    val eColsMap = g.edgeColumnMap

    //println("vColsMap", vColsMap)
    //println("eColsMap", eColsMap)

    // Convert vertex attributes to nice case classes.
    val gx1: Graph[VertexAttr, Row] = gx0.mapVertices { case (_, attr) =>
      VertexAttr(attr.getLong(vColsMap("id")), attr.getLong(vColsMap("color")))
    }

    // Convert edge attributes to nice case classes.
    val extractEdgeAttr: (GXEdge[Row] => EdgeAttr) = { e =>
      val src = e.attr.getLong(eColsMap("src"))
      val dst = e.attr.getLong(eColsMap("dst"))
      val dist = e.attr.getDouble(eColsMap("dist"))
      EdgeAttr(src, dst, dist)
    }

    var gx: Graph[VertexAttr, EdgeAttr] = gx1.mapEdges(extractEdgeAttr)
    for (iter <- Range(1,maxIter)) {
      val msgs: VertexRDD[Msg] = gx.aggregateMessages(
        ctx =>
          // Can send to source or destination since edges are treated as undirected.
          if (ctx.srcAttr.color.toInt <= (k + 1) && ctx.dstAttr.color.toInt > (k + 1)) {
            val msg = Msg(1 << ctx.srcAttr.color.toInt)
            ctx.sendToDst(msg)
          }, chooseColorMsg)

      // Update neighbors
      gx = gx.outerJoinVertices(msgs) {
        case (vID, vAttr, optMsg) =>
          val msg = optMsg.getOrElse(Msg(-23))
          if (vAttr.color > (k + 1) && msg.value != -23) {
           VertexAttr(vAttr.srcId, getMinColor(msg))
          } else {
            vAttr
          }
      }
      //println("Iter ", iter)
    }

    // Convert back to GraphFrame with a new column "belief" for vertices DataFrame.
    // Inorder to deal with disconnected components
    val gxFinal: Graph[Long, Unit] = gx.mapVertices((_, attr) => if (attr.color > k+1) 0 else attr.color )
      .mapEdges(_ => ())

    //gxFinal.edges.foreach(println)
    //gxFinal.vertices.foreach(println)
    GraphFrame.fromGraphX(g, gxFinal, vertexNames = Seq("finalColor"))
  }

  def colorGraphReductionNaive(g: GraphFrame, k: Int): GraphFrame = {

      val n = g.vertices.count().toInt
      val gx0 = g.toGraphX

      // Schema maps for extracting attributes
      // Edges should have an attribute called color
      val vColsMap = g.vertexColumnMap
      val eColsMap = g.edgeColumnMap

      //println("vColsMap", vColsMap)
      //println("eColsMap", eColsMap)

      // Convert vertex attributes to nice case classes.
      val gx1: Graph[VertexAttr, Row] = gx0.mapVertices { case (_, attr) =>
        VertexAttr(attr.getLong(vColsMap("id")), attr.getLong(vColsMap("color")))
      }

      // Convert edge attributes to nice case classes.
      val extractEdgeAttr: (GXEdge[Row] => EdgeAttr) = { e =>
        val src = e.attr.getLong(eColsMap("src"))
        val dst = e.attr.getLong(eColsMap("dst"))
        val dist = e.attr.getDouble(eColsMap("dist"))
        EdgeAttr(src, dst, dist)
      }

      var gx: Graph[VertexAttr, EdgeAttr] = gx1.mapEdges(extractEdgeAttr)
      for (color <- Range(k + 2,n)) {
        val msgs: VertexRDD[Msg] = gx.aggregateMessages(
          ctx =>
            // Can send to source or destination since edges are treated as undirected.
            if (ctx.dstAttr.color.toInt == color) {
              val msg = Msg(1 << ctx.srcAttr.color.toInt)
              ctx.sendToDst(msg)
            }, chooseColorMsg)

        // Update neighbors
        gx = gx.outerJoinVertices(msgs) {
          case (vID, vAttr, optMsg) =>
            if (vAttr.color == color) {
              val msg = optMsg.getOrElse(Msg(0))
              VertexAttr(vAttr.srcId, getMinColor(msg))
            } else {
              vAttr
            }
        }
      }

    // Convert back to GraphFrame with a new column "belief" for vertices DataFrame.
    // Inorder to deal with disconnected components
    val gxFinal: Graph[Long, Unit] = gx.mapVertices((_, attr) => if (attr.color > k+1) 0 else attr.color )
      .mapEdges(_ => ())

    val g2 = GraphFrame.fromGraphX(g, gxFinal, vertexNames = Seq("finalColor"))
    val vertices = g2.vertices.drop("color").withColumnRenamed("finalColor", "color")
    GraphFrame(vertices,g.edges)

  }
}

