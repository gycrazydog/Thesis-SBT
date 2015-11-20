package hbase.master.thesis.graph.partitioner
import org.apache.spark.SparkContext._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.graphx.PartitionStrategy._
import java.io._
import scala.collection.mutable.HashMap
object Ordered {
var TEMPERATURE = 2.0
  val TEMPERATUREDelta = 0.01
  var sparkMaster = ""
  var cassandraMaster = ""
  var path = ""
  var tableName = "testgraph";
  var colorNum = 0
  var color: Array[Int] = Array()
  var toNeighbors : Map[Int,Set[Int]] = Map.empty
  var fromNeighbors : Map[Int,Set[Int]] = Map.empty
  def main(args:Array[String]) = {
      path  = args(0)
      sparkMaster = args(1)  
      val output_path = args(2)
      colorNum = args(3).toInt
      val sparkConf = new SparkConf().setAppName("JabeJa : ").setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
  //    JabeJa_Local
  //    JabeJa_Random
      println("JabeJa_Random!")
      val edges = sc.textFile(path, colorNum).map(line=>{
                val edge = line.split(" ")
                Edge(edge(0).toLong,edge(1).toLong,edge(2))
      }).collect()
      val neighbors : Map[Int,Array[Int]] = edges.flatMap(e=>Array((e.srcId.toInt,e.dstId.toInt)
                                                                    ,(e.dstId.toInt,e.srcId.toInt)))
                                                     .groupBy(f=>f._1).mapValues(f=>f.map(v=>v._2))
      val nodes = (neighbors.keys).toArray.max+1
      println("node number : "+nodes)
      var initColor : List[Int] = List()
      for(x<-0 to colorNum-2){
        initColor ++= List.fill(nodes/colorNum)(x)
      }
      initColor ++= List.fill(nodes-nodes/colorNum*(colorNum-1))(colorNum-1)
      color = initColor.toArray
      println("inputnode start input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates.size )
      println("start cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) ).size )
      val writer = new PrintWriter(new File(output_path))
      color.foreach(v=>writer.write(v+"\n"))
      writer.close()
    }
}