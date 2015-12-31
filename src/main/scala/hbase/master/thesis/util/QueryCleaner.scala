package hbase.master.thesis.util
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import java.io._
import scala.io.Source
object QueryCleaner {
  def main(args: Array[String]) = {
      val dataset_path  = args(0)
      val sparkMaster = args(1)  
      val sparkConf = new SparkConf().setAppName("Create Histogram: ").setMaster(sparkMaster)
      val sc = new SparkContext(sparkConf)
      val rdd = sc.textFile(dataset_path, 3).cache
      val randomrec = new PrintWriter(new File("/home/crazydog/ALIBABA/query/random-recursive/queries.txt"))  
//      val realrec = new PrintWriter(new File("/home/crazydog/ALIBABA/query/real-recursive/queries.txt"))  
      val randomplain = new PrintWriter(new File( "/home/crazydog/ALIBABA/query/random-plain/queries.txt")) 
//      val realplain = new PrintWriter(new File("/home/crazydog/ALIBABA/query/real-plain/queries.txt"))
      rdd.filter(line=>line.contains("*")==false&&line.contains("?")==false&&line.contains("+")==false).collect().foreach(line=>randomplain.write(line+"\n"))
      rdd.filter(line=>line.contains("*")&&line.contains("?")==false&&line.contains("+")).collect().foreach(line=>randomrec.write(line+"\n"))
      randomrec.close
      randomplain.close
  }
}