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
import java.io._
import scala.io.Source
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
    val auto = GraphReader.automata(sc,path,workerNum)
    val automata = auto.edges
    val finalState = auto.vertices.filter(v=>v._2=="final").map(v=>v._1).collect().toSet
    var ans : Set[(String,String)] = new HashSet()
    val labelset = automata.collect.map(v=>v.attr).toSet
    val histogram = sc.textFile(Histogram, workerNum).map(line=>(line.split(" ")(0),line.split(" ")(1).toInt)).collect.toMap
    val queryList = GraphReader.compileQuery(auto,histogram)
    val queryPlan = sc.parallelize(queryList._1, workerNum)
//    queryPlan.foreach(v=>println("spanning edge : "+v))
//    queryPlan.collect().foreach(println)
    val queryMap = queryPlan.map(v=>(v._2,v._1)).groupByKey().map(v=>(v._1,v._2.toArray)).collect().toMap
//    queryMap.foreach(v=>println("query map : "+v._1+v._2))
    val rs = queryPlan.map(v=>(v._1,histogram.get(v._2.attr).get)).reduceByKey(_+_).collect()
                          .sortBy(f=>f._1)
                          .map(f=>f._2)
    rs.foreach(println("rs : ",_))
    val as = SolveLagrangean.solve(rs, 64).map(Math.ceil(_).toInt)
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
    
    var currentStates = allEdges.join(queryPlan.map(e=>(e._2.attr,e._2)))
                        .flatMap(f=>{
                          val states = queryMap.get(f._2._2).get
                          states.map(v=>( v, ( (f._2._1._1,f._2._2.srcId) , (f._2._1._2,f._2._2.dstId)) ) )
                        })
                        .repartition(workerNum)
                        .cache()
//    println("current states size : "+currentStates.count)
    val newStates = currentStates.flatMap(v=>{
      var var1 = v._1.toInt-1
      var var2 = v._1.toInt
//      println("key1 : "+Math.abs(v._2._1.hashCode())%as(var1))
//      println("key2 : "+Math.abs(v._2._2.hashCode())%as(var2))
      keys.filter(key=>key(var1)==Math.abs(v._2._1.hashCode())%as(var1)&&key(var2)==Math.abs(v._2._2.hashCode())%as(var2))
      .map(key=>(key.mkString("+"),List(v)))
    }).reduceByKey((a,b)=>a++b).cache()
//    println("size of newStates : "+newStates.count())
//    newStates.collect().foreach(println("newStates ",_))
    val temprdd = newStates.map(v=>{
      val masterStates = v._2.toList.groupBy(_._1).mapValues(v=>v.map(k=>k._2))
      var current = masterStates.getOrElse(1L,List()).map(k=>k.swap).toSet
      var visited : HashSet[((String,VertexId),(String,VertexId))] = new HashSet()
      for(i <- 2L to rs.length){
        visited = visited ++ current
        val nextStates = masterStates.getOrElse(i, List()).groupBy(_._1).mapValues(f=>f.map(k=>k._2))
        val nextCurrent = current.filter(v=>nextStates.contains(v._1))
                          .flatMap(v=>nextStates.get(v._1).get.map((_,v._2)))
                          .filter(v=>visited.contains(v)==false)
                          .toSet
        current = nextCurrent
      }
      visited = visited ++ current
      visited.toList
    })
//    println("temprdd size : "+temprdd.count)
    var currentTrans = sc.parallelize(queryList._2, workerNum)
    var continuePoints = currentTrans.map(e=>e.srcId).collect.toSet
    var visitedStates : RDD[((String,VertexId),(String,VertexId))] = temprdd.flatMap(f=>f).cache
//        visitedStates.collect.foreach(println("visited state : ",_))
    println("visited states size :"+visitedStates.count())
    var continueStates = visitedStates.filter(f=>continuePoints.contains(f._1._2)).cache()
    println("continue states size : "+continueStates.count())
    while(continueStates.count()>0){
      val nextEdges = allEdges.join(currentTrans.map(e=>(e.attr,e)))
                        .map(f=> ((f._2._1._1,f._2._2.srcId) , (f._2._1._2,f._2._2.dstId)) )
                        .repartition(workerNum)
                        .cache()
      val nextStates = nextEdges
                        .join(continueStates)
                        .map(v=>v._2)
                        .subtract(visitedStates)
                        //.filter(!visitedStates.contains(_))
                        .distinct()
      continueStates = nextStates.cache
      val nextTotalStates = visitedStates.union(continueStates).coalesce(workerNum)
      visitedStates = nextTotalStates
      val nextTrans = currentTrans.map(v=>(v.dstId,v))
                        .join(automata.map(v=>(v.srcId,v)))
                        .map(v=>v._2._2)
                        .distinct
      currentTrans = nextTrans
    }
    ans = ans ++ visitedStates.filter(v=>finalState.contains(v._1._2)&&v._2._2==0L).map(v=>(v._2._1,v._1._1)).collect()
//      ans.map(v=>println("vertex reached!!! "+v))
    println("number of pairs : "+ans.size)
    println("-------------------------------------------------------------")
    ans
  }
  def main(args:Array[String]){
      path = args(0)
      tableName = args(1)
      var maxt = Long.MinValue
      var mint = Long.MaxValue
      var sumt = 0L
      val sparkMaster = args(2)
      val histogram = args(4)
      val sparkConf = new SparkConf().setAppName("Multiway Join : "+path+" "+tableName).setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
      val files = sc.wholeTextFiles(path).map(_._1).collect()
//      files.foreach(println)
      files.map(v=>{
         path = v
         val startTime = System.currentTimeMillis
         val asn = run(sc,args(3).toInt,histogram) 
         val endTime = System.currentTimeMillis
         val t = (endTime-startTime)
         sumt = sumt + t
         if(t>maxt) maxt = t
         if(t<mint) mint = t
         println("time : "+t)
      })
      println("maxt : ",maxt)
      println("mint : ",mint)
      println("avgt : ",sumt/files.size)
      
//      val files = new File("/home/crazydog/ALIBABA/query/random-recursive/empty-result/")
//                  .list
////                  .filter(v=>v.contains("1072"))
//      val specials = files.filter(filename=>filename.endsWith(".txt")).map(file=>{
//        println("filename : "+file)
//        val auto = GraphReader.automata(sc,"/home/crazydog/ALIBABA/query/random-recursive/empty-result/"+file)
//        val automata = auto.edges
//        val finalState = auto.vertices.filter(v=>v._2=="final").map(v=>v._1).collect().toSet
//        val startTime = System.currentTimeMillis 
//        var ans : Set[(String,String)] = new HashSet()
//        val labelset = automata.collect.map(v=>v.attr).toSet
//        val hist = sc.textFile(histogram, 3).map(line=>(line.split(" ")(0),line.split(" ")(1).toInt)).collect.toMap
//        val queryList = GraphReader.compileQuery(auto,hist)
//        val queryPlan = sc.parallelize(queryList._1, 3)
//    //    queryPlan.foreach(v=>println("spanning edge : "+v))
//    //    queryPlan.collect().foreach(println)
//        val queryMap = queryPlan.map(v=>(v._2,v._1)).groupByKey().map(v=>(v._1,v._2.toArray)).collect().toMap
//    //    queryMap.foreach(v=>println("query map : "+v._1+v._2))
//        val rs = queryPlan.map(v=>(v._1,hist.get(v._2.attr).get)).reduceByKey(_+_).collect()
//                              .sortBy(f=>f._1)
//                              .map(f=>f._2)
//        rs.foreach(println("rs : ",_))
//        val as = SolveLagrangean.solve(rs, 64)
//        as.foreach(println("as : ",_))
//        if(as.filter(v=>v<1).size>0) file+" less than 1!"
//        else file+" clean"
//      })
//      specials.foreach(println)
//      println("less than 1 size : ",specials.filter(v=>v.contains("less than 1")).size)
  }
}