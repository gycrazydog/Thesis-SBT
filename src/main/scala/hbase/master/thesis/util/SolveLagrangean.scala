package hbase.master.thesis.util

object SolveLagrangean {
  def solve(R : Array[Int], k : Int) : Array[Int] = {
    val r = 0.0+:R.map(_.toDouble)
    val n = r.length-1
    var a = Array.ofDim[Double](n+1)
    a(0) = 1
    a(n) = 1
    //n even
    if(n%2==0){
      val temp = Array.range(1, n/2+1, 1).map(j=> r(2) /r(1)*(r(2*j-1)/r(2*j)) ).reduceLeft(_*_)
      a(2) = Math.pow(temp, 2/n.toDouble)
      var evenCal = a(2)
      for(i <- 2 to (n-2)/2 ){
        a(2*i) = Math.pow(a(2),i)*Array.range(2, i+1, 1).map(j=> r(1)/r(2)*(r(2*j)/r(2*j-1)) ).reduceLeft(_*_)
        evenCal = evenCal*a(2*i)
      }
      val c = Array.range(0, (n-2)/2+1 ).map(i=>( r(2*i+1)/(r(1)*a(2*i)) )).reduceLeft(_*_)
      a(1) = Math.pow(k.toDouble/c/evenCal,2/n.toDouble)
      for(i <- 1 to (n-2)/2 ){
        a(2*i+1) =  a(1)*r(2*i+1)/(r(1)*a(2*i))
      }
    }
    //n odd
    else{
      var c = 1.0
      for( i <- 1 to (n-1)/2 ){
        c = c*Array.range(1, i+1).map(j=>r(1) /r(2)*(r(2*j)/r(2*j-1)) ).reduceLeft(_*_)
        c = c*Array.range(1, i+1).map(j=>r(1) / r(2)*(r(n-2*j+1)/r(n-2*j+2)) ).reduceLeft(_*_)
      }
      println("c  = ",c)
      a(2) = Math.pow(k.toDouble/c,4.0/((n.toDouble+1)*(n.toDouble-1)))
      println("a(2) = ",a(2))
      for(i <- 1 to (n-1)/2 ){
        a(2*i) = Math.pow(a(2),i)*Array.range(1, i+1).map(j=>r(1)/r(2)*(r(2*j)/r(2*j-1)) ).reduceLeft(_*_)
//        println(i+" "+a(2*i))
//        Array.range(1, i+1).map(j=>{
//          println("j = "+j)
//          println(r(1)/r(2))
//          println(r(2*j)/r(2*j-1)) 
//        })
        a(n-2*i) = Math.pow(a(2),i)*Array.range(1, i+1).map(j=>r(1)/r(2)*(r(n-2*j+1)/r(n-2*j+2)) ).reduceLeft(_*_)
      }
    }
    a.foreach(println("Double a : ",_))
    a.map(Math.ceil(_).toInt)
  }
  def main(args: Array[String]) = {
    val ans = solve(Array(26025,306617,26025,306617,306617,306617,26025),64)
    ans.foreach(println)
  }
}