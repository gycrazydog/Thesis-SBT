package regularPathQuery
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import com.datastax.spark.connector._
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.PartitionStrategy._
object GraphWriter {
  def writeAlibaba(sc:SparkContext):Unit = {
    val rdd = sc.textFile("/home/crazydog/ALIBABA/alibaba.graph.txt", 3).map(line=>{
                val edge = line.split(" ")
                ( (edge(0),edge(2)) ,  Set(edge(1) ) )
              }).reduceByKey((a, b) => ( a++b ) ).map(v=>(v._1._1,v._1._2,v._2))
    rdd.saveToCassandra("mykeyspace", "graph", SomeColumns("srcid","label","dstid"))
    println("afterput")
  }
  def writeTest(sc:SparkContext):Unit = {
    val rdd = sc.textFile("/home/crazydog/test/TestGraph.txt", 3).map(line=>{
                val edge = line.split(" ")
                ( (edge(0),edge(1)) ,  Set(edge(2) ) )
              }).reduceByKey((a, b) => ( a++b ) ).map(v=>(v._1._1,v._1._2,v._2))
    rdd.saveToCassandra("mykeyspace", "testgraph", SomeColumns("srcid","label","dstid"))
    println("afterput")
  }
  def main(args:Array[String]) = {
    val sparkConf = new SparkConf().setAppName("HBaseMultipleThread").setMaster("local[4]")
    .set("spark.cassandra.connection.host", "127.0.0.1")
    val sc = new SparkContext(sparkConf)
    writeAlibaba(sc)
//    println("13000 : "+Bytes.toBytes("13000").map("%02X" format _).mkString)
//    println("14000 : "+Bytes.toBytes("20000").map("%02X" format _).mkString)
//    println("21000 : "+Bytes.toBytes("30000").map("%02X" format _).mkString)
//    val ids = sc.parallelize(Array.range(0,1).map(i=>i.toLong))
//    println("res size:"+GraphReader.getEdgesByIds(sc, ids).count())
  }
}