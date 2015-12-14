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
import hbase.master.thesis.util.SolveLagrangean
object MultiWayJoin {
  var path = "";
  var tableName = "testgraph";
  implicit val config = HBaseConfig(
    "hbase.rootdir" -> "hdfs://hadoop-m:8020/hbase",
    "hbase.zookeeper.quorum" -> "hadoop-m"
  )
  def genKeys(curLevel : Int,buckets : Array[Int],curKeys : Array[Int], varNum : Int) : Array[Array[Int]] = {
    var temp : List[Array[Int]]= List()
    if(curLevel < varNum){
      for(i <- 0 to buckets(curLevel)-1){
        val nextKeys = curKeys :+ i
        temp ++= genKeys(curLevel+1,buckets,nextKeys,varNum)
      }
    }
    else{
      temp :+= curKeys
    } 
//    println(curLevel + " "+ temp.size )
    temp.toArray
  }
  def run(sc:SparkContext,workerNum:Int,Histogram: String):Set[(String,String)] = {
      println("------------------------------start"+" "+path+tableName+"--------------------------")
    val auto = GraphReader.automata(sc,path)
    val automata = auto.edges
    val vertices = auto.vertices.count()
    val finalState = HashSet(vertices)
    val startTime = System.currentTimeMillis 
    var ans : Set[(String,String)] = new HashSet()
    val labelset = automata.collect.map(v=>v.attr).toSet
    val histogram = sc.textFile(Histogram, workerNum).map(line=>(line.split(" ")(0),line.split(" ")(1).toInt)).collect.toMap
    var curStart = 1L
    var curAutos = automata.filter(e=>e.srcId==1L).cache
    var nextAutos = curAutos.map(e=>(e.dstId,e)).join(automata.map(e=>(e.srcId,e)))
                      .map(s=>s._2._2)
                      .distinct()
    var curPlan = curAutos.map(e=>((curStart,curStart+1),e)).cache()
    var size = 0
    while(nextAutos.subtract(curAutos).count()>0){
//      curPlan.collect().foreach(println(curStart," cur plan : ",_))
      println("cur plan number : "+curPlan.count)
      curAutos = nextAutos
      val temp = curAutos.map(e=>((curStart,curStart+1),e ) ) 
//      curPlan.collect().foreach(println(curStart," cur plan : ",_))
      val nextPlan = curPlan.union(temp).repartition(workerNum)
      curPlan = nextPlan
//      curPlan.collect().foreach(println(curStart," next plan : ",_))
      curStart = curStart+1
      nextAutos = curAutos.map(e=>(e.dstId,e)).join(automata.map(e=>(e.srcId,e)))
                      .map(s=>s._2._2)
                      .distinct()
    }
    val queryPlan = curPlan
//    queryPlan.collect().foreach(println)
    val queryMap = queryPlan.map(v=>(v._2,v._1)).groupByKey().map(v=>(v._1,v._2.toArray)).collect().toMap
    queryMap.foreach(v=>println("query map : "+v._1+v._2.length))
    val rs = queryPlan.map(v=>(v._1,histogram.get(v._2.attr).get)).reduceByKey(_+_).collect()
                          .sortBy(f=>f._1._1)
                          .map(f=>f._2)
    rs.foreach(println("rs : ",_))
    val as = SolveLagrangean.solve(rs, 64)
    as.foreach(println("as : ",_))
    val keys = genKeys(0,as,Array(),as.size)
    val columns = Map(
      "to"   -> labelset    
    )
    //RDD[((edge.dstid,autoedge.dstid),(edge.srcid,autoedge.srcid))]
    val allEdges = sc.hbase[String](tableName, columns)
                       .flatMap(v=>v._2.values.map(k=>(v._1,k)))
                       .flatMap(v=>v._2.map(k=>(v._1,k._1,k._2)))
                       .flatMap(v=>v._3.split(":").map(k=>(v._2,(v._1,k))))
                       .cache()
    
    var currentStates = allEdges.join(automata.map(e=>(e.attr,e)))
                        .flatMap(f=>{
                          val states = queryMap.get(f._2._2).get
                          states.map(v=>( ((f._2._1._2,f._2._2.dstId),v._2) , ((f._2._1._1,f._2._2.srcId),v._1)   ))
                        })
                        .repartition(workerNum)
                        .cache()
    // no self loop anymore
    val newStates = currentStates.flatMap(v=>{
      var var1 = v._1._2.toInt-1
      var var2 = v._2._2.toInt-1
      keys.filter(key=>key(var1)==Math.abs(v._1._1.hashCode())%as(var1)&&key(var2)==Math.abs(v._2._1.hashCode())%as(var2))
      .map(key=>(key.mkString("+"),List(v)))
    }).reduceByKey((a,b)=>a++b).cache()
//    println("size of newStates : "+newStates.count())
//    newStates.collect().foreach(println("newStates ",_))
    val temprdd = newStates.map(v=>{
      var tempans : HashSet[(String,String)] = new HashSet()
      val masterStates = v._2.toArray
      val nextMasterStates = masterStates.map(v=>(v._2,v._1)).groupBy(_._1).mapValues(f=>f.map(_._2))
      //(dst,src)
      var current = masterStates.filter(k=>k._2._2==1L).toSet
      var visited : HashSet[((String,VertexId),(String,VertexId))] = new HashSet()
      for(i <- 1 to rs.length-1){
        visited = visited ++ current.map(v=>(v._1._1,v._2._1))
//        println("current : ",current.size)
  //      current.foreach(println("state ",_))
        val stopStates = current.filter(p=>p._2._1._2==1L&&finalState.contains(p._1._1._2))
        tempans = tempans ++ stopStates.map(f=>(f._2._1._1,f._1._1._1))
        val nextCurrent = current.filter(v=>nextMasterStates.contains(v._1))
                          .flatMap(v=>nextMasterStates.get(v._1).get.map((_,v._2)))
                          .filter(v=>visited.contains((v._1._1,v._2._1))==false)
                          .toSet
        current = nextCurrent
      }
      (current,tempans,visited)
    })
    ans = temprdd.flatMap(f=>f._2).collect.toSet
    var endStates = temprdd.flatMap(f=>f._1).map(f=>(f._1._1,f._2._1))
    val selfStates = currentStates.filter(f=>f._2._2==rs.length).map(f=>(f._2._1,f._1._1))
    var visitedStates : RDD[((String,VertexId),(String,VertexId))] = temprdd.flatMap(f=>f._3)
    println("self states : "+selfStates.count())
    println("endStates number :"+endStates.count())
    while(endStates.count()>0){
      val nextTotalStates = visitedStates.union(endStates).coalesce(workerNum)
      visitedStates = nextTotalStates
      ans = ans ++ endStates.filter(v=>finalState.contains(v._1._2)&&v._2._2==1L).map(v=>(v._2._1,v._1._1)).collect()
      val nextStates = selfStates
                        .join(endStates)
                        .map(v=>v._2)
                        .subtract(visitedStates)
                        //.filter(!visitedStates.contains(_))
                        .distinct()
      endStates = nextStates.cache
    }
    
    val endTime = System.currentTimeMillis
//      ans.map(v=>println("vertex reached!!! "+v))
    println("number of pairs : "+ans.size)
    println("time : "+(endTime-startTime))
    println("-------------------------------------------------------------")
    ans
  }
  def main(args:Array[String]){
      path = args(0)
      tableName = args(1)
      val sparkMaster = args(2)
      val histogram = args(4)
      val sparkConf = new SparkConf().setAppName("Multiway Join : "+path+" "+tableName).setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
      for(x <- 1 to 10){
        val asn = run(sc,args(3).toInt,histogram)
      }
        
  }
}