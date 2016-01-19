package hbase.master.thesis.graph.partitioner
import org.apache.spark.SparkContext._
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.graphx.PartitionStrategy._
import java.io._
import scala.collection.mutable.HashMap
object JabeJa_InputHybrid {
  var TEMPERATURE = 20.0
  val TEMPERATUREDelta = 0.1
  var sparkMaster = ""
  var cassandraMaster = ""
  var path = ""
  var tableName = "testgraph";
  var colorNum = 0
  var round = 350
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
  def JabeJa_InputNodeLocal(sc:SparkContext) : Unit = {
    val edges = sc.textFile(path, 3).map(line=>{
                  val edge = line.split(" ")
                  Edge(edge(0).toLong,edge(1).toLong,edge(2))
    }).collect()
    val writer = new PrintWriter(new File(path+"jabeja-inputlocal.round."+colorNum+".txt"))
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
//    color = initColor.toArray
    var InDegrees : Array[Int] = Array.ofDim[Int](nodes)
    fromNeighbors.foreach(f=>{
      InDegrees(f._1)=f._2.size
      f._2.foreach(v=>fromNeighborColors(f._1)(color(v)) = fromNeighborColors(f._1)(color(v))+1)
    })
//    fromNeighborColors.foreach(println)
    val r = scala.util.Random
    var reduced = 0
    writer.write(List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees)).size+"\n")
    println("inputnode start input nodes : " + List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees)).size )
    for(counter <- 0 to round){
      for(currentNode <- 0 to nodes-1){
        //      val currentNode = r.nextInt().abs%nodes
        var bestNeibour = -1;
        var leastIN = -1.0;
        val currentNeighbors = toNeighbors.getOrElse(currentNode,Set())
        //if currentnode is inputnode
        currentNeighbors.filter(color(currentNode)!=color(_)).foreach(v=>{
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
         if(bestNeibour==(-1)){//No local swap, try 30 random nodes
                var RandomNeighbors : Set[Int]= Set()
                for(x<-1 to 5){
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
         }
         if(bestNeibour==(-1)){//No local swap, try 30 random nodes
            
         }
         else{
//           println("swap between "+currentNode+ " and "+bestNeibour+" with counter = "+counter)
    //          println("before input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
    //                                                 .map(x=>x.dstId).toList.removeDuplicates.size )
           swap(currentNode,bestNeibour)
           updateColor(currentNode,bestNeibour,fromNeighborColors,toNeighbors.getOrElse(currentNode,Set()))
           updateColor(bestNeibour,currentNode,fromNeighborColors,toNeighbors.getOrElse(bestNeibour,Set()))
//           println("reduced input nodes : "+leastIN)
           if(TEMPERATURE-TEMPERATUREDelta>=1) TEMPERATURE = TEMPERATURE-TEMPERATUREDelta
           else TEMPERATURE = 1
         }
        }
        val in = List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees)).size
        val ce = edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt)).size
        writer.write(counter+","+in+"," + ce+"\n")
        println("round : "+counter+" current cross edges : " + ce )
        println("current input nodes : " + in)
      }
      writer.close()
      println("final input nodes : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt) )
                                                   .map(x=>x.dstId).toList.removeDuplicates.size)
      println("final input nodes : " + List.range(0,nodes).filter(isInputNode(_,fromNeighborColors,InDegrees)).size)
      println("final cross edges : " + edges.filter( e=>color(e.dstId.toInt)!=color(e.srcId.toInt)).size )
    }

  def main(args:Array[String]) = {
      path  = args(0) 
      val output_path = args(1)
      colorNum = args(2).toInt
      round = args(3).toInt
      val sparkConf = new SparkConf().setAppName("JabeJa : ")
      val sc = new SparkContext(sparkConf)
  //    JabeJa_Local
  //    JabeJa_Random
      println("JabeJa_Random!")
      JabeJa_InputNodeLocal(sc)
      val writer = new PrintWriter(new File(output_path))
      color.foreach(v=>writer.write(v+"\n"))
      writer.close()
    }
}