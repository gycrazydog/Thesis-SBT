package hbase.master.thesis.regular.path.query
import unicredit.spark.hbase._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import scala.collection.immutable.HashSet
import scala.collection.mutable.HashMap
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import hbase.master.thesis.util.GraphReader
import org.apache.spark.graphx.PartitionStrategy._
object DanAlgorithm {
  case class SrcId(srcid : Long) 
  case class Complex(srcid: Long,auto: Edge[String],edge: Edge[String])
  var path = "";
  var tableName = "testgraph";
  var sparkMaster = "";
  implicit val config = HBaseConfig(
    "hbase.rootdir" -> "hdfs://hadoop-m:8020/hbase",
    "hbase.zookeeper.quorum" -> "hadoop-m"
  )
  def run(sc:SparkContext,workerNum:Int):Unit = {
    //val nodes = sc.parallelize( 1 to 26, 3)
    var columns = Map(
      "property"   ->  Set("inputnode")    
    )
    val rdd = sc.hbase[String](tableName,columns)
    val inputnodes = rdd.filter(v=>v._2.get("property").get.contains("inputnode")).map(v=>v._1).collect().toSet
    println("input node size "+inputnodes.size)
    var masterStates : HashSet[((String, VertexId), (String, VertexId))] = new HashSet()
    val auto = GraphReader.automata(sc,path)
    val automata = auto.edges
    val finalState = HashSet(auto.vertices.count().toLong)
    val startTime = System.currentTimeMillis 
    var currentTrans = automata.filter(e=>e.srcId==1L)
    val labelset = currentTrans.map(v=>v.attr).collect.toSet
    val inputNodes = sc.hbase[String](tableName,Set("to"))
                        .filter(v=>inputnodes.contains(v._1))
                        .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                        .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                        .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                        
    columns = Map(
      "to"   -> labelset    
    )
    val startNodes = sc.hbase[String](tableName,columns)
                        .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                        .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                        .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                        
    val inputStates = inputNodes.join(automata.filter(e=>e.srcId!=1L).map(e=>(e.attr,e)))
                         .map(f=>((f._2._1._2,f._2._2.dstId),(f._2._1._1,f._2._2.srcId)))
                         .cache()
    val startStates = startNodes.join(currentTrans.map(e=>(e.attr,e))) 
                         .map(f=>((f._2._1._2,f._2._2.dstId),(f._2._1._1,f._2._2.srcId)))
                                      .cache()
    println("startStates number : "+startStates.count())
    println("inputStates number : "+inputStates.count())
    var currentStates = inputStates.union(startStates)
                        .coalesce(workerNum).cache()
    currentTrans = automata
//    println("the inputnode number==11 : ",currentStates.filter(f=>f._1.srcId==3&&f._1.dstId==4
//                                                        &&f._1.attr=="6"
//                                                        &&f._2._3.split("-")(0).toInt==4889)
//                                                       .count()
//                                                       )
    //currentStates.collect().foreach(println("init state : ",_))
    var visitedStates : RDD[((String, VertexId), (String, VertexId))] = sc.emptyRDD
    var size = currentStates.count()
    var i = 0
    while(size>0){
      val nextTotalStates = visitedStates.union(currentStates).coalesce(workerNum)
      visitedStates = nextTotalStates
      i = i+1
      println("iteration:"+i)
      println("currentStates : ",size)
      println("current MasterStates : ",masterStates.size)
      //Add final states or states with output node
//      println("output states: ",currentStates.filter(f=>f._2._3.split("-")(1).toInt==0).count())
//      println("final auto states: ",currentStates.filter(f=>finalState.contains(f._1.dstId)).count())
        masterStates = masterStates ++ currentStates.filter(f=>(inputnodes.contains(f._1._1))
                                                            || finalState.contains(f._1._2)
                                                            )
                                                            .collect()                                         
       //State transition     
        val nextCurrentTrans = currentTrans.map(v=>(v.dstId,v)).join(automata.map(v=>(v.srcId,v)))
                                .map(v=>v._2._2)
        currentTrans = nextCurrentTrans.distinct()
        val labelset = currentTrans.map(v=>v.attr).distinct().collect().toSet
        val columns = Map(
            "to"   -> labelset    
        )
        var nextEdges : RDD[((String,VertexId),(String,VertexId))] = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                       .join(currentTrans.map(e=>(e.attr,e)))
                       .map(f=>((f._2._1._1,f._2._2.srcId),(f._2._1._2,f._2._2.dstId)))
        val nextStates = nextEdges
                        .join(currentStates.filter(f=>false==inputnodes.contains(f._1._1) ) )
                        .map(v=>v._2)
                        .subtract(visitedStates)
                        .cache()
        currentStates = nextStates
        size = currentStates.count()
//      val nextGlobalMatches = visitedStates.union(currentStates)
//      visitedStates = nextGlobalMatches
//      val nextStates = currentStates.
    }
    println("masterStates : ",masterStates.size)
    println("small fragments : ",masterStates.filter(v=>v._1._2-v._2._2==1&&false==finalState.contains(v._1._2)).size)
    println("reached final state ",masterStates.filter(v=>finalState.contains(v._1._2)).size)
    println("reached output node ",masterStates.filter(v=>inputnodes.contains(v._1._1)).size)
    var ans : HashSet[(String,String)] = new HashSet()
    var visited : HashSet[((String,VertexId),(String,VertexId))] = new HashSet()
    var current = masterStates.filter(p=>p._2._2==1L)
    val nextMasterStates = masterStates.map(v=>(v._2,v._1)).groupBy(_._1).mapValues(f=>f.map(_._2))
    while(current.size>0){
      visited = visited ++ current
      println("current : ",current.size)
//      current.foreach(println("state ",_))
      val stopStates = current.filter(p=>p._2._2==1L&&finalState.contains(p._1._2))
      ans = ans ++ stopStates.map(f=>(f._2._1,f._1._1))
      val nextCurrent = current.filter(v=>nextMasterStates.contains(v._1))
                        .flatMap(v=>nextMasterStates.get(v._1).get.map((_,v._2)))
                        .filter(visited.contains(_)==false)                          
      current = nextCurrent
    }
    println("ans size : ",ans.size)
    val endTime = System.currentTimeMillis
    println("time : "+(endTime-startTime))
    println("-------------------------------------------------------------")
//    ans.foreach(println("pair found :",_))
  }
  def main(args:Array[String]) = {
    path = args(0)
    tableName = args(1)
    sparkMaster = args(2)
    val sparkConf = new SparkConf().setAppName("DanAlgorithm : "+path).setMaster(sparkMaster)
    val sc = new SparkContext(sparkConf)
    println("------------------------------start"+path+"--------------------------")
//    init(sc)
    run(sc,args(3).toInt+1)
  }
}