package yarn.hbase.master.thesis.regular.path.query
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
import java.io._
import scala.io.Source
import hbase.master.thesis.util.GraphReader
import org.apache.spark.graphx.PartitionStrategy._
object DanAlgorithm {
  case class SrcId(srcid : Long) 
  case class Complex(srcid: Long,auto: Edge[String],edge: Edge[String])
  var path = "";
  var tableName = "testgraph";
  var sparkMaster = "";
  implicit val config = HBaseConfig()
  def run(sc:SparkContext,workerNum:Int):Int = {
    println("------------------------------start"+path+"--------------------------")
    //val nodes = sc.parallelize( 1 to 26, 3)
    var columns = Map(
      "property"   ->  Set("inputnode")    
    )
    val rdd = sc.hbase[String](tableName,columns)
    val inputnodes = rdd.filter(v=>v._2.get("property").get.contains("inputnode")).map(v=>v._1).collect().toSet
    println("input node size "+inputnodes.size)
    var masterStates : HashSet[((String, VertexId), (String, VertexId))] = new HashSet()
    val auto = GraphReader.automata(sc,path,workerNum)
    val automata = auto.edges
    val finalState = auto.vertices.filter(v=>v._2=="final").map(v=>v._1).collect().toSet
    var excludeTrans = automata.filter(e=>e.srcId!=0L)
    val excludeMap = excludeTrans.map(k=>(k.attr,k)).groupByKey().collect().toMap
    var currentTrans = automata.filter(e=>e.srcId==0L)
    val currentMap = currentTrans.map(e=>(e.attr,e)).groupByKey().collect().toMap
    val labelset = currentMap.keys.toSet
    columns = Map(
      "to"   ->  excludeMap.keys.toSet
    )
    val inputNodes = sc.hbase[String](tableName,columns)
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
            
    val inputStates = inputNodes
                       .flatMap(v=>excludeMap.getOrElse(v._1, List()).toList.map(e=>( (v._2._2,e.dstId),(v._2._1,e.srcId) )))
                       .cache()
    val startStates = startNodes
                       .flatMap(v=>currentMap.getOrElse(v._1, List()).toList.map(e=>( (v._2._2,e.dstId),(v._2._1,e.srcId) )))
                       .cache()
    println("startStates number : "+startStates.count())
    println("inputStates number : "+inputStates.count())
//    println("start states partition number : ",startStates.partitions.size)
//    startStates.foreachPartition(v=>println("start state each partition : ",v.size))
//    println("input states partition number : ",inputStates.partitions.size)
//    inputStates.foreachPartition(v=>println("input state each partition : ",v.size))
    var currentStates = inputStates.zipPartitions(startStates,true){(iter1,iter2)=>iter1++iter2}.cache
    currentTrans = automata
//    println("current states partition number : ",currentStates.partitions.size)
//    currentStates.foreachPartition(v=>println("current state each partition : ",v.size))
//    //currentStates.collect().foreach(println("init state : ",_))
    var visitedStates : RDD[((String, VertexId), (String, VertexId))] = currentStates
    var size = currentStates.count()
    var i = 0
    while(size>0){
      val nextTotalStates = visitedStates.zipPartitions(currentStates,true){(iter1,iter2)=>(iter1++iter2).toSet.toIterator}.cache
      visitedStates = nextTotalStates
      println("visited states : ",visitedStates.count)
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
        currentTrans = nextCurrentTrans.distinct
        val labelMap = currentTrans.map(e=>(e.attr,e)).groupByKey().collect().toMap
        val labelset = currentTrans.map(v=>v.attr).collect().toSet
        val columns = Map(
            "to"   -> labelset    
        )
        val formerStates = currentStates.filter(f=>false==inputnodes.contains(f._1._1) ).mapPartitions(f=>{
          val res = f.toList.groupBy(_._1)
          res.map(v=>(v._1,v._2.map(k=>k._2))).toIterator
        }, true)
        var nextEdges : RDD[((String,VertexId),(String,VertexId))] = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                       .flatMap(v=>labelMap.getOrElse(v._1, List()).toList.map(e=>( (v._2._1,e.srcId),(v._2._2,e.dstId) )))
        val nextStates = nextEdges
                        .zipPartitions(formerStates,true)
                                      {(iter1,iter2)=>{
                                        val nextStates = iter1.toList     
                                        val lastStates = iter2.toMap
                                        val res = nextStates.flatMap(s=>lastStates.getOrElse(s._1, List()).map(v=>(s._2,v)))
                                        res.toIterator
                         }}
                        .zipPartitions(visitedStates,true)
                                      {(iter1,iter2)=>{  
                                        val lastStates = iter2.toSet
                                        iter1.toList.filter(p=>false==lastStates.contains(p)).toIterator
                         }}
                        .cache()
        currentStates = nextStates
        size = currentStates.count()
//      val nextGlobalMatches = visitedStates.union(currentStates)
//      visitedStates = nextGlobalMatches
//      val nextStates = currentStates.
    }
    println("masterStates : ",masterStates.size)
//    println("small fragments : ",masterStates.filter(v=>v._1._2-v._2._2==1&&false==finalState.contains(v._1._2)).size)
//    println("reached final state ",masterStates.filter(v=>finalState.contains(v._1._2)).size)
//    println("reached output node ",masterStates.filter(v=>inputnodes.contains(v._1._1)).size)
    var ans : HashSet[(String,String)] = new HashSet()
    var visited : HashSet[((String,VertexId),(String,VertexId))] = new HashSet()
    var current = masterStates.filter(p=>p._2._2==0L)
    val nextMasterStates = masterStates.map(v=>(v._2,v._1)).groupBy(_._1).mapValues(f=>f.map(_._2))
    while(current.size>0){
      visited = visited ++ current
      println("current : ",current.size)
//      current.foreach(println("state ",_))
      val stopStates = current.filter(p=>p._2._2==0L&&finalState.contains(p._1._2))
      ans = ans ++ stopStates.map(f=>(f._2._1,f._1._1))
      val nextCurrent = current.filter(v=>nextMasterStates.contains(v._1))
                        .flatMap(v=>nextMasterStates.get(v._1).get.map((_,v._2)))
                        .filter(visited.contains(_)==false)                          
      current = nextCurrent
    }
    println("ans size : ",ans.size)
    println("-------------------------------------------------------------")
//    ans.foreach(println("pair found :",_))
    ans.size
  }
  def main(args:Array[String]){
      path = args(0)
      tableName = args(1)
      var maxt = Long.MinValue
      var mint = Long.MaxValue
      var hascount = 0
      var hast = 0L
      var zerot = 0L
      var zerocount = 0
      var sumt = 0L
      val sparkConf = new SparkConf().setAppName("Dan Algorithm : "+path+" "+tableName)
      val sc = new SparkContext(sparkConf)
      val files = new File(path).listFiles
      println("query files number : ",files.length)
//      files.foreach(println)
      files.map(v=>{
         path = v.toString
         val startTime = System.currentTimeMillis
         val asn = run(sc,args(2).toInt) 
         val endTime = System.currentTimeMillis
         val t = (endTime-startTime)
         sumt = sumt + t
         if(asn == 0) { zerot = zerot + t; zerocount = zerocount+1; }
         else { hast = hast + t; hascount = hascount + 1; }
         if(t>maxt) maxt = t
         if(t<mint) mint = t
         println("counter : "+(zerocount+hascount)+"  time : "+t)
      })
      println("maxt : ",maxt)
      println("mint : ",mint)
      if(zerocount>0)println("zerot : ",zerot/zerocount)
      println("zerocount : ",zerocount)
      if(hascount>0)println("hast : ",hast/hascount)
      println("hascount : ",hascount)
      println("avgt : ",sumt/files.size)
  }
}