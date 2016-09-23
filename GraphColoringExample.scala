object GraphColoringExample {

  import GraphColoring._

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("example").setMaster("local[2]")
    val sc = SparkContext.getOrCreate(conf)
    val sqlContext = SQLContext.getOrCreate(sc)

    // Create graphical model g of size 3 x 3.
    val nodes = sqlContext
      .createDataFrame(List(
        (0, "A", 0.0, 0.0, 3, 0,0),
        (1, "a", 1.0, 1.0, 2, 1,1),
        (2, "b", 1.1, 1.0, 1, 1,2),
        (3, "c", 1.2, 1.0, 1, 1,3),
        (4, "d", 1.3, 1.0, 1, 1,4),
        (5, "e", 1.4, 1.0, 1, 1,5),
        (7, "B", 1.5, 1.1, 1, 1,6),
        (8, "C", 1.5, 1.2, 1, 1,7),
        (9, "D", 1.5, 1.3, 1, 1,8),
        (10, "E", 1.5, 1.4, 1, 1,9),
        (11, "F", 1.5, 1.5, 1, 1,10),
        (12, "Bb", 1.5, 2.1, 1, 1,11),
        (13, "Cc", 1.5, 2.2, 1, 1,12),
        (14, "Dd", 1.5, 2.3, 1, 1,13),
        (15, "Ee", 1.5, 2.4, 1, 1,14),
        (16, "Ff", 1.5, 2.5, 1, 1,15),
        (17, "Gg", 1.5, 2.6, 1, 1,16)))
      .toDF("index", "mark", "longitude", "latitude", "outDegree", "inDegree","colorInt")

    val createPointClassUdf = udf((long: Double, lat: Double) => Point(long, lat))
    val toLongUdf = udf((i: Int) => i.toLong)
    val sumUdf = udf((i1:Int,i2:Int) => i1+i2)

    /*
    val vertices = nodes.withColumn("coordinates", createPointClassUdf(col("longitude"), col("latitude")))
      .withColumn("id", toLongUdf(col("index"))).drop(col("index"))
        .withColumn("color", toLongUdf(col("colorInt")))
        .withColumn("degree", sumUdf(col("inDegree"), col("outDegree")))
          .sort(col("color").desc)
      */

    val id = (1 to nodes.count.toInt).toSeq.map(i => i.toLong)
    val rows = nodes.rdd.collect.zip(id).map { case (r, index) => Row.fromSeq(r.toSeq :+ index) }
    val struct = nodes.schema.add("color", LongType, false)

    val vertices = sqlContext.createDataFrame(sc.parallelize(rows), struct)
                  .withColumn("id", toLongUdf(col("index"))).drop(col("index"))

    val connections =
      sqlContext.createDataFrame(List(
        (0, 1, 0.7),
        (1, 2, 0.7),
        (2, 3, 0.2),
        (3, 4, 1.2),
        (4, 5, 1.3),
        (1, 7, 0.7),
        (7, 8, 0.4),
        (8, 9, 0.4),
        (9, 10, 0.4),
        (10, 11, 0.8),
        (0, 12, 0.5),
        (12, 13, 0.6),
        (13, 14 , 0.8),
        (14, 15, 0.7),
        (15, 16, 0.8),
        (16, 17, 0.9)))
        .toDF("srcIndex", "dstIndex", "dist")

    val edges = connections.withColumn("src",toLongUdf(col("srcIndex"))).withColumn("dst", toLongUdf(col("dstIndex")))
      .select(col("src"),col("dst"),col("dist"))
    val g = GraphFrame(vertices, edges)

    // Run BP for 5 iterations.
    println("Original Graph Model :")
    vertices.printSchema()

    println("Edges: ")
    edges.printSchema()

    val g2 = colorGraphReductionFastest(g,4)
    g2.vertices.show()
    g2.edges.show()

    val diffUdf = udf((i1:Int,i2:Int) => if (i1==i2) 1 else 0)
    val count =  g2.edges.join(g2.vertices, g2.edges.col("src").equalTo(g2.vertices.col("id")),"inner")

      .withColumn("srcColor", g2.vertices.col("finalColor"))
      .select(col("srcColor"),col("dst"), col("src"))
      .join(g2.vertices, g2.edges.col("dst").equalTo(g2.vertices.col("id")),"inner")
      .withColumn("dstColor", g2.vertices.col("finalColor"))
      .select(col("srcColor"),col("dst"), col("src"),col("dstColor"))
      .withColumn("diffColor",diffUdf(col("srcColor"),col("dstColor")))
      .select("diffColor").agg(sum(col("diffColor"))).collect()(0)(0).asInstanceOf[Long]

    println("Count = ", count)

    val msgs = List(1,2,4,5).map(i => Msg(1 << i)).fold(Msg(0))(chooseColorMsg)
    println("original:", msgs)
    println("result" ,getMinColor(msgs))

    // Done with Coloring
    sc.stop()
  }

}


