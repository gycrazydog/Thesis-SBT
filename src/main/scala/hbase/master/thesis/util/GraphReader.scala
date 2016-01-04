package hbase.master.thesis.util
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.collection.mutable.HashMap
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
object GraphReader {
  def automata(sc : SparkContext,path : String,workerNum: Int) : Graph[Any,String] = {
     val rdd = sc.textFile(path, workerNum).cache()
     val max_id = rdd.filter(line=>line.split(" ")(0)!="final").map(line=>line.split(" ").dropRight(1).map(v=>v.toLong).max).max()
     val finalstates = rdd.filter(line=>line.split(" ")(0)=="final").map(line=>line.split(" ")(1).toLong).collect().toSet
     val temp = List.range(0L, max_id+1)
     val states : RDD[(VertexId,(Any))]= sc.parallelize(temp.map(v=>{
       if(finalstates.contains(v)) (v,("final"))
       else (v,("non-final"))
     }),1)
     val trans = rdd.filter(line=>line.split(" ")(0)!="final").map(line=>{
        val edge = line.split(" ")
        Edge(edge(0).toLong,edge(1).toLong,edge(2))
      })
      val defaultUser = ("John Doe", "Missing")
      val graph = Graph(states, trans,defaultUser)
      graph
  }
  def compileQuery(g : Graph[Any,String],histogram : Map[String,Int]) : (List[(Long,Edge[String])],List[Edge[String]]) = {
    val edges = g.edges.collect.toList
//    println("edges : ",edges.size)
    var ans : List[(Long,Edge[String])] = List()
    var stepMap : HashMap[VertexId,Int] = new HashMap()
    stepMap.put(0L, 0)
    var visitedNodes : Set[VertexId]= Set(0L)
    var visitedEdges : Set[Edge[String]] = Set()
    for(i <- 1 to g.vertices.count().toInt-1){
      val newEdges = edges.filter(e=>visitedEdges.contains(e)==false)
                           .filter(e=>visitedNodes.contains(e.srcId))
                           .filter(e=>visitedNodes.contains(e.dstId)==false)
      val currentEdge = newEdges.map(e=>(e,histogram.get(e.attr).get)).minBy(_._2)._1
      visitedEdges += currentEdge
      visitedNodes += currentEdge.dstId
      stepMap.put(currentEdge.dstId,stepMap.get(currentEdge.srcId).get+1)
      ans :+= ( (stepMap.get(currentEdge.srcId).get.toLong+1,currentEdge) )
    }
    ans.foreach(println("spanning edge : ",_))
    (ans,edges.diff(visitedEdges.toList))
  }
  def main(args:Array[String]) = {
     val path = args(0)
     val sparkConf = new SparkConf().setAppName("Graph Reader").setMaster("local")
     val sc = new SparkContext(sparkConf)
     val rdd = automata(sc,path,3)
     rdd.vertices.filter(v=>v._2=="final").collect().foreach(f=>println(f))
  }
}