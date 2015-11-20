package hbase.master.thesis.graph.partitioner
import org.apache.spark.SparkContext._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.graphx.PartitionStrategy._
import java.io._
import scala.collection.mutable.HashMap
object JabeJa_InputRandom {
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
  
  def swap(cur : Int,neigh : Int) :Unit = {
      val temp = color(neigh)
      color(neigh) = color(cur)
      color(cur) = temp;
  }
  def updateColor(cur : Int,neigh : Int,fromNeighborColors: Array[Array[Int]],Neighbors : Set[Int]) : Unit = {
      Neighbors.foreach(v=>{
        fromNeighborColors(v)(color(cur)) = fromNeighborColors(v)(color(cur)) +1
        fromNeighborColors(v)(color(neigh)) = fromNeighborColors(v)(color(neigh))-1
      })
  }
  def isInputNode(currentNode : Int,fromNeighborColors: Array[Array[Int]],InDegrees : Array[Int]) : Boolean = {
    return fromNeighborColors(currentNode)(color(currentNode))!=InDegrees(currentNode)
  }
   def JabeJa_InputNodeRandom(sc:SparkContext) : Unit = {
    val edges = sc.textFile(path, colorNum).map(line=>{
                  val edge = line.split(" ")
                  Edge(edge(0).toLong,edge(1).toLong,edge(2))
    }).collect()
    val writer = new PrintWriter(new File(path+"jabeja-inputrandom.round."+colorNum+".txt"))
    toNeighbors = edges.map(e=>(e.srcId.toInt,e.dstId.toInt)).groupBy(f=>f._1).map(f=>(f._1,f._2.map(k=>k._2).toSet))
    fromNeighbors= edges.map(e=>(e.dstId.toInt,e.srcId.toInt)).groupBy(f=>f._1).map(f=>(f._1,f._2.map(k=>k._2).toSet))
    val nodes = (toNeighbors.keys++fromNeighbors.keys).toArray.max+1
    var fromNeighborColors = Array.ofDim[Int](nodes,colorNum)
    println("node number : "+nodes)
    var initColor : List[Int] = List()
    for(x<-0 to colorNum-2){
      initColor ++= List.fill(nodes/colorNum)(x)
    }
    initColor ++= List.fill(nodes-nodes/colorNum*(colorNum-1))(colorNum-1)
    color = scala.util.Random.shuffle(initColor).toArray
    var InDegrees : Array[Int] = Array.ofDim[Int](nodes)
    fromNeighbors.foreach(f=>{
      InDegrees(f._1)=f._2.size
      f._2.foreach(v=>fromNeighborColors(f._1)(color(v)) = fromNeighborColors(f._1)(color(v))+1)
    })
    var counter = 0
    val r = scala.util.Random
    var reduced = 0
    val pp = edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates
    val qq = List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees))
    writer.write(qq.size+"\n")
    println("edges start input nodes : " + pp.size )
    println("inputnode start input nodes : " + qq.size )
    println("start cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) ).size )
    pp.diff(qq).foreach(println("p-q ",_))
    qq.diff(pp).foreach(println("q-p ",_))
    for(counter <- 0 to 350){
      for(currentNode <- 0 to nodes-1){
  //      val currentNode = 8381 
        var bestNeibour = -1;
        var leastIN = -1.0;
        val currentNeighbors = toNeighbors.getOrElse(currentNode,Set())
        var RandomNeighbors : Set[Int]= Set()
        for(x<-1 to edges.size/nodes*2){
           var newNeighbor = scala.util.Random.nextInt.abs%nodes
           if(newNeighbor<0) newNeighbor = newNeighbor+nodes
           while(color(currentNode)==color(newNeighbor)||RandomNeighbors.contains(newNeighbor)==true) {  
             newNeighbor = scala.util.Random.nextInt%nodes
             if(newNeighbor<0) newNeighbor = newNeighbor+nodes
           }
           RandomNeighbors.+=(newNeighbor)
        }
        //if currentnode is inputnode
        RandomNeighbors
            .foreach(v=>{
            val vNeibours = toNeighbors.getOrElse(v,Set())
            val pNeibours = currentNeighbors.diff(vNeibours+v)
            val qNeibours = vNeibours.diff(currentNeighbors+currentNode)
            var pinc = pNeibours.filter(k=>isInputNode(k,fromNeighborColors,InDegrees)==false).size
            var pdec = pNeibours.filter(k=>fromNeighborColors(k)(color(k))==(InDegrees(k)-1)
                                                        &&color(k)==color(v)).size
            if(vNeibours.contains(currentNode)==false&&InDegrees(currentNode)>0){
              if(isInputNode(currentNode,fromNeighborColors,InDegrees)==false) pinc = pinc + 1
              else if(fromNeighborColors(currentNode)(color(v))==InDegrees(currentNode)) pdec = pdec + 1
            }
            var qinc = qNeibours.filter(k=>isInputNode(k,fromNeighborColors,InDegrees)==false).size
            var qdec = qNeibours.filter(k=>fromNeighborColors(k)(color(k))==(InDegrees(k)-1)
                                                        &&color(k)==color(currentNode)).size
            if(currentNeighbors.contains(v)==false&&InDegrees(v)>0){
              if(isInputNode(v,fromNeighborColors,InDegrees)==false) qinc = qinc + 1
              else if(fromNeighborColors(v)(color(currentNode))==InDegrees(v)) qdec  = qdec + 1
            }
            val diff = (pdec+qdec).toDouble*TEMPERATURE - (pinc+qinc).toDouble
  //          val diff = (pp+qq) - (pq+qp)
            if(diff>0){
               if(leastIN==(-1)||diff>leastIN){
                  bestNeibour = v.toInt
                  leastIN = diff
               }
            }
         })
         if(bestNeibour==(-1)){
//           println("no swap between "+currentNode+ " and "+bestNeibour+" with counter = "+counter)
         }
         else{
//           println("swap between "+currentNode+ " and "+bestNeibour+" with counter = "+counter)    //          println("before input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
    //                                                 .map(x=>x.dstId).toList.removeDuplicates.size )
           swap(currentNode,bestNeibour)
           updateColor(currentNode,bestNeibour,fromNeighborColors,toNeighbors.getOrElse(currentNode,Set()))
           updateColor(bestNeibour,currentNode,fromNeighborColors,toNeighbors.getOrElse(bestNeibour,Set()))
           if(TEMPERATURE-TEMPERATUREDelta>=1) TEMPERATURE = TEMPERATURE-TEMPERATUREDelta
           else TEMPERATURE = 1
         }
        }
        writer.write("round : "+counter+"current input nodes : " + List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees)).size+"\n")
        println("round : "+counter+" current cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt)).size )
        println("current input nodes : " + List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees)).size)
      }
      writer.close()
      println("final input nodes : " + List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees)).size)
      println("final cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt)).size )
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
      println("JabeJa_Random!")
      JabeJa_InputNodeRandom(sc)
      val writer = new PrintWriter(new File(output_path))
      color.foreach(v=>writer.write(v+"\n"))
      writer.close()
    }
}