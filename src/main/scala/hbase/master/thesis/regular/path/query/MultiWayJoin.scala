package hbase.master.thesis.regular.path.query
import unicredit.spark.hbase._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.collection.immutable.HashSet
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
import hbase.master.thesis.util.GraphReader
object MultiWayJoin {
  var path = "";
  var tableName = "testgraph";
  implicit val config = HBaseConfig()
  def run(sc:SparkContext,workerNum:Int):Set[(String,String)] = {
      println("------------------------------start"+path+"--------------------------")
    val auto = GraphReader.automata(sc,path)
    val automata = auto.edges
    val finalState = HashSet(auto.vertices.count().toLong)
    val startTime = System.currentTimeMillis 
    var ans : Set[(String,String)] = new HashSet()
    val labelset = automata.collect.map(v=>v.attr).toSet
    val columns = Map(
      "to"   -> labelset    
    )
    //RDD[((edge.dstid,autoedge.dstid),(edge.srcid,autoedge.srcid))]
    val startedges = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
    var currentStates =  startedges.join(automata.map(e=>(e.attr,e))) 
                         .map(f=>((f._2._1._2,f._2._2.dstId),(f._2._1._1,f._2._2.srcId)))
                         .cache()
      var size = currentStates.count()
      var visitedStates : RDD[((String,VertexId),(String,VertexId))] = sc.emptyRDD
      var i = 0
      while(size>0){
//        currentStates.collect().foreach(println("current state : ",_))
        val nextTotalStates = visitedStates.union(currentStates).coalesce(workerNum)
        visitedStates = nextTotalStates.cache()
//        println("CUrrent Partitions : "+currentStates.partitions.length)
//        println("Current States : "+nextTotalStates.count())
//        println("Current Trans : "+currentTrans.length)
        i = i+1
        println("iteration:"+i)
        println("current States size :"+size)   
        //        currentStates.collect.foreach(v=>println("current State : "+v))
        ans = ans ++ currentStates.filter(v=>finalState.contains(v._1._2)&&v._2._2==1L ).map(v=>(v._2._1,v._1._1)).collect()
        println("Answer Size : "+ans.size)
        //RDD[((edge.dstid,autoedge.dstid),edge.startid)]
        //RDD[((edge.srcid,autoedge.srcid),(edge.dstid,autoedge.dstid))]
        val nextStates = currentStates.map(v=>(v._2,v._1))
                        .join(visitedStates)
                        .map(v=>v._2)
                        .subtract(visitedStates) 
                        //.filter(!visitedStates.contains(_))
                        .distinct()
//        println("iteration : "+i+ " count : "+nextStates.collect())
        currentStates = nextStates.cache
        println("current states partition number : "+currentStates.partitions.size)
        size = currentStates.count()
        println("finishing calculating currentStates!")
      }
      val endTime = System.currentTimeMillis
//      ans.map(v=>println("vertex reached!!! "+v))
      println("number of pairs : "+ans.size)
      println("time : "+(endTime-startTime))
      println("-------------------------------------------------------------")
      ans
  }
  def main(args:Array[String]){
      val sparkConf = new SparkConf().setAppName("CassandraMultipleThread : "+path).setMaster("local[3]")
      val sc = new SparkContext(sparkConf)
      path = args(0)
      tableName = args(1)
      val asn = run(sc,args(2).toInt)
  }
}