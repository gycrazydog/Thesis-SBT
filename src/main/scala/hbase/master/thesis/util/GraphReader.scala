package hbase.master.thesis.util
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD
object GraphReader {
  def automata(sc : SparkContext,path : String) : Graph[Any,String] = {
     val rdd = sc.textFile(path, 3).cache()
     val max_id = rdd.filter(line=>line.split(" ").size<=2).map(line=>line.split(" ")).toArray()(0)(0).toInt
     val states : RDD[(VertexId,(Any))]= sc.parallelize(1L to max_id,1).cartesian(sc.parallelize(Array(()), 1))
      val trans = rdd.filter(line=>line.split(" ").size==3).map(line=>{
        val edge = line.split(" ")
        Edge(edge(0).toLong,edge(1).toLong,edge(2))
      })
      val defaultUser = ("John Doe", "Missing")
      val graph = Graph(states, trans,defaultUser)
      graph
  }
}