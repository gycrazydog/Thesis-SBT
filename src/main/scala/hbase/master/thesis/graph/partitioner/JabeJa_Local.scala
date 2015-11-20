package hbase.master.thesis.graph.partitioner
import org.apache.spark.SparkContext._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.graphx.PartitionStrategy._
import java.io._
import scala.collection.mutable.HashMap
object JabeJa_Local {
  var TEMPERATURE = 1.0
  val TEMPERATUREDelta = 0.01
  var sparkMaster = ""
  var cassandraMaster = ""
  var path = ""
  var tableName = "testgraph";
  var keyspace = "";
  var colorNum = 0
  var color: Array[Int] = Array()
  
  def swap(cur : Int,neigh : Int) :Unit = {
      val temp = color(neigh)
      color(neigh) = color(cur)
      color(cur) = temp;
      
  }
  def updateColor(cur : Int,neigh : Int,fromNeighborColors: Array[Array[Int]],Neighbors : Array[Int]) : Unit = {
      Neighbors.foreach(v=>{
        fromNeighborColors(v)(color(cur)) = fromNeighborColors(v)(color(cur)) +1
        fromNeighborColors(v)(color(neigh)) = fromNeighborColors(v)(color(neigh))-1
      })
  }
  def JabeJa_Local(sc:SparkContext) : Unit = {
  val edges = sc.textFile(path, colorNum).map(line=>{
                val edge = line.split(" ")
                Edge(edge(0).toLong,edge(1).toLong,edge(2))
  }).collect()
  val writer = new PrintWriter(new File(path+"jabeja-local.round.txt"))
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
  color = scala.util.Random.shuffle(initColor).toArray
//  color = initColor.toArray
  var NeighborColors = Array.ofDim[Int](nodes,colorNum)
  var InDegrees : Array[Int] = Array.ofDim[Int](nodes)
  neighbors.foreach(f=>{
      InDegrees(f._1)=f._2.size
      f._2.foreach(v=>NeighborColors(f._1)(color(v)) = NeighborColors(f._1)(color(v))+1)
  })
  println("inputnode start input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates.size )
  println("start cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) ).size )
  writer.write(edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates.size+"\n")
  var currentCrossEdgesNum = edges.filter(v=>color(v.dstId.toInt)!=color(v.srcId.toInt)).size
  val r = scala.util.Random
  for(counter <- 0 to 350){
    for(currentNode <- 0 to nodes-1){
      var bestNeibour = -1;
      var leastCE = -1.0;
      val currentNeighbors = neighbors.get(currentNode).get
      currentNeighbors.filter(color(currentNode)!=color(_)).foreach(v=>{
          val betweenEdges = currentNeighbors.filter(_==v).size
          var pinc = NeighborColors(currentNode)(color(currentNode))
          var pdec = NeighborColors(currentNode)(color(v))-betweenEdges
          var qinc = NeighborColors(v)(color(v))
          var qdec = NeighborColors(v)(color(currentNode))-betweenEdges
//          println(currentNode+" "+v)
//          println("numbers : "+pinc+" "+pdec+" "+qinc+" "+qdec)
          val diff = (pdec+qdec).toDouble*TEMPERATURE - (pinc+qinc).toDouble
          if(diff>0){
               if(leastCE==(-1)||diff>leastCE){
                  bestNeibour = v.toInt
                  leastCE = diff
               }
           }
        } 
      )
      if(bestNeibour==(-1)){
//        println("no swap for "+currentNode)
      }
      else{
//        println("swap between "+currentNode+ " and "+bestNeibour+" with counter = "+counter)
//        println("before cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) ).size )
        swap(currentNode,bestNeibour)
        updateColor(currentNode,bestNeibour,NeighborColors,neighbors.getOrElse(currentNode,Array()))
        updateColor(bestNeibour,currentNode,NeighborColors,neighbors.getOrElse(bestNeibour,Array()))
//        println("reduced cross edges : "+leastCE)
        if(TEMPERATURE-TEMPERATUREDelta>=1) TEMPERATURE = TEMPERATURE-TEMPERATUREDelta
        else TEMPERATURE = 1
      }
    }
    writer.write(edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt)).size+"\n")
    println("round : "+counter+" current cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt)).size )
    println("current input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates.size)
   }
    writer.close()
    println("final cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt)).size )
    println("final input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates.size)
  }
  def main(args:Array[String]) = {
    path  = args(0)
    sparkMaster = args(1)  
    val output_path = args(2)
    colorNum = args(3).toInt
    val sparkConf = new SparkConf().setAppName("JabeJa : ").setMaster(sparkMaster)
    val sc = new SparkContext(sparkConf)
//    JabeJa_Local
//    JabeJa_Random
    println("JabeJa_Local!")
    JabeJa_Local(sc)
    val writer = new PrintWriter(new File(output_path))
    color.foreach(v=>writer.write(v+"\n"))
    writer.close()
  }
}