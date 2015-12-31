package hbase.master.thesis.util

object SolveLagrangean {
  case class Relation(id: Int, value : Int)
  def firstSolve(R : Array[Int], k : Int) : Array[Double] = {
    if(R.size==1) return Array(1.0,1.0)
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
      a(2) = Math.pow(k.toDouble/c,4.0/((n.toDouble+1)*(n.toDouble-1)))
      for(i <- 1 to (n-1)/2 ){
        a(2*i) = Math.pow(a(2),i)*Array.range(1, i+1).map(j=>r(1)/r(2)*(r(2*j)/r(2*j-1)) ).reduceLeft(_*_)
        a(n-2*i) = Math.pow(a(2),i)*Array.range(1, i+1).map(j=>r(1)/r(2)*(r(n-2*j+1)/r(n-2*j+2)) ).reduceLeft(_*_)
      }
    }
    println("finished normal solve:")
    a = a.map(v=>if(Math.abs(1-v)<1e-6) 1
                      else v)
    a.foreach(println("Double a : ",_))
    a
  }
  def getNext(R1 : Array[Int], R2 : Array[Int], pre: Double, k: Int) : Double = {
    val r1 = 0.0+:R1.map(_.toDouble)
    val r2 = 0.0+:R2.map(_.toDouble)
    val n1 = r1.size-1
    val n2 = r2.size-1
    if(n1%2==0){
          val temp1 = Array.range(1, n1/2+1, 1).map(j=> r1(2)/r1(1)*(r1(2*j-1)/r1(2*j)) ).reduceLeft(_*_)
          val a12 = Math.pow(temp1, 2/n1.toDouble)
          if(n2%2==0){
            val temp2 = Array.range(1, n2/2+1, 1).map(j=> r2(2)/r2(1)*(r2(2*j-1)/r2(2*j)) ).reduceLeft(_*_)
            val a22 = Math.pow(temp2, 2/n2.toDouble)
            val a21 = (r2(1)+r2(2)/a22)/(r1(1)+r1(2)/a12)*pre
            return a21
          }
          else{
//            println("temp1 : "+temp1 + "  a12 : "+a12)
            val c2 = Array.range(1, (n2-1)/2+1).map(j=>r2(1)/r2(2)*(r2(n2-2*j+1)/r2(n2-2*j+2)) ).reduceLeft(_*_)
            val left = (r1(1)+r1(2)/a12)/pre/c2
            var c = 1.0
            for( i <- 1 to (n2-1)/2 ){
              c = c*Array.range(1, i+1).map(j=>r2(1) /r2(2)*(r2(2*j)/r2(2*j-1)) ).reduceLeft(_*_)
              c = c*Array.range(1, i+1).map(j=>r2(1) / r2(2)*(r2(n2-2*j+1)/r2(n2-2*j+2)) ).reduceLeft(_*_)
            }
            var l = 0.0
            var r = Math.pow(k.toDouble/c,4.0/((n2.toDouble+1)*(n2.toDouble-1)))
//            println("r : ",r)
            while(r-l>1e-6){
              val a22 = (l+r)/2
              val res = r2(1)/Math.pow(a22, (n2-1)/2)+r2(2)/Math.pow(a22, (n2+1)/2)
//              println("l = "+l+" r= "+r)
//              println("a22 = "+a22+"res : "+res)
              if(res>left) l = a22
              else r = a22
            }
            return l
          }
        }
        else{
          val c1 = Array.range(1, (n1-1)/2+1).map(j=>r1(1)/r1(2)*(r1(n1-2*j+1)/r1(n1-2*j+2)) ).reduceLeft(_*_)
          val left = (r1(1)/Math.pow(pre, (n1-1)/2)+r1(2)/Math.pow(pre, (n1+1)/2))*c1
//          println("pre = "+pre+" c1 = "+c1+" left= "+left)
          if(n2%2==0){
            val temp2 = Array.range(1, n2/2+1, 1).map(j=> r2(2)/r2(1)*(r2(2*j-1)/r2(2*j)) ).reduceLeft(_*_)
            val a22 = Math.pow(temp2, 2/n2.toDouble)
            val a21 = (r1(1)+r1(2)/a22)/left
            return a21
          }
          else{
            val c2 = Array.range(1, (n2-1)/2+1).map(j=>r2(1)/r2(2)*(r2(n2-2*j+1)/r2(n2-2*j+2)) ).reduceLeft(_*_)
            var c = 1.0
            for( i <- 1 to (n2-1)/2 ){
              c = c*Array.range(1, i+1).map(j=>r2(1) /r2(2)*(r2(2*j)/r2(2*j-1)) ).reduceLeft(_*_)
              c = c*Array.range(1, i+1).map(j=>r2(1) / r2(2)*(r2(n2-2*j+1)/r2(n2-2*j+2)) ).reduceLeft(_*_)
            }
            var l = 0.0
            var r = Math.pow(k.toDouble/c,4.0/((n2.toDouble+1)*(n2.toDouble-1)))
//            println("c2 = "+c2)
            while(r-l>1e-6){
              val a22 = (l+r)/2
//              println("a22 = "+a22)
              val res = r2(1)/Math.pow(a22, (n2-1)/2)+r2(2)/Math.pow(a22, (n2+1)/2)
              if(res>left/c2) l = a22
              else r = a22
            }
            return l
          }
        }
  }
  def subproduct(a1: Double, R : Array[Int]) : Double = {
    val r = 0.0+:R.map(_.toDouble)
    val n = r.size-1
    if(n%2==0){
      return Math.pow(a1, n/2)*Array.range(0, n/2, 1).map(j=> r(2*j+1)/r(1) ).reduceLeft(_*_)
    }
    else{
      var c = 1.0
      for( i <- 1 to (n-1)/2 ){
        c = c*Array.range(1, i+1).map(j=>r(2*j)/r(2*j-1) ).reduceLeft(_*_)
        c = c*Array.range(1, i+1).map(j=>r(n-2*j+1)/r(n-2*j+2) ).reduceLeft(_*_)
      }
//      println("n = "+n+" c = "+c)
      return Math.pow(a1/r(2)*r(1), (n*n-1)/4+1)*c
    }
  }
  def solveSubChain(start : Double,R: Array[Int]) : Array[Double] = {
    val r = 0.0+:R.map(_.toDouble)
    val n = r.size-1
    var ans = Array.ofDim[Double](n+1)
    ans(0) = 1
    ans(n) = 1
    if(n%2==0){
      val temp = Array.range(1, n/2+1, 1).map(j=> r(2) /r(1)*(r(2*j-1)/r(2*j)) ).reduceLeft(_*_)
      ans(2) = Math.pow(temp, 2/n.toDouble)
      ans(1) = start
      println("n = "+n)
      println(ans(1)+"    "+ans(2))
      for(i <- 1 to (n-2)/2 ){
        ans(2*i) = Math.pow(ans(2),i)*Array.range(1, i+1, 1).map(j=> r(1)/r(2)*(r(2*j)/r(2*j-1)) ).reduceLeft(_*_)
        ans(2*i+1) =  ans(1)*r(2*i+1)/(r(1)*ans(2*i))
      }
      ans
    }
    else{
      for(i <- 1 to (n-1)/2 ){
        ans(2*i) = Math.pow(start,i)*Array.range(1, i+1).map(j=>r(1)/r(2)*(r(2*j)/r(2*j-1)) ).reduceLeft(_*_)
        ans(n-2*i) = Math.pow(start,i)*Array.range(1, i+1).map(j=>r(1)/r(2)*(r(n-2*j+1)/r(n-2*j+2)) ).reduceLeft(_*_)
      }
      ans
    }
  }
  def specialSolve(R : Array[Int],anchorPoints : Array[Int], k : Int) : Array[Double] = {
    var left = 0.0
    var right = k.toDouble
    val anchors = 0 +: anchorPoints
    var length : Array[Int]= Array()
    for(i <- 1 to anchors.length-1){
      length :+= anchors(i)-anchors(i-1)
    }
    length :+= R.length-anchors.last
//    length.foreach(println("length: ",_))
    while(right-left>1e-6){
      val mid = ((left+right)/2).toDouble
      var a1 = mid
      var temp_k = subproduct(a1,R.slice(0,length(0)))
//      println("temp_k = "+temp_k)
      for(i <- 0 to anchors.length-2){
        val ai1 = getNext(R.slice(anchors(i),anchors(i)+length(i)),R.slice(anchors(i+1),anchors(i+1)+length(i+1)),a1,k)
//        println("ai1 : i = "+(i+1)+" anchor = "+anchors(i+1)+"  ai1 = "+ai1)
        temp_k *= subproduct(ai1,R.slice(anchors(i+1),anchors(i+1)+length(i+1)))
//        println("temp_k = "+temp_k)
        a1 = ai1
      }
      if(temp_k>k) right = mid
      else left = mid
//      println("a1 = "+a1+"  temp_k = "+temp_k+"  left = "+left+"   right = "+right)
    }
    var final_a = left
//    println("final a = ",final_a)
    var ans : Array[Double]= Array()
    ans ++= solveSubChain(final_a,R.slice(anchors(0),anchors(0)+length(0)))
    for(i <- 0 to anchors.length-2){
      val final_next_a = getNext(R.slice(anchors(i),anchors(i)+length(i)),R.slice(anchors(i+1),anchors(i+1)+length(i+1)),final_a,k)
      final_a = final_next_a
      val sub_result = solveSubChain(final_a,R.slice(anchors(i+1),anchors(i+1)+length(i+1)))
      ans ++= sub_result.slice(1, sub_result.length)
    }
    ans.foreach(println("solved a : ",_))
    ans = ans.map(v=>if(Math.abs(1-v)<1e-6) 1
                      else v)
    return ans
  }
  def solve(input : Array[Int], k : Int) : Array[Double] = {
    val R = input.zipWithIndex.map(v=>Relation(v._2,v._1))
    var removed_r = Array()
    var cur_R = R
    var ans = firstSolve(R.map(v=>v.value),k)
    var counter = 0
    while(ans.filter(v=>v<1).size>0){
      println("iteration : "+counter)
      cur_R.foreach(r=>println("cur r : ",r.id," ",r.value))
      val nextR = cur_R.zipWithIndex.filter(v=>ans(v._2)-1>1e-6||ans(v._2+1)-1>1e-6)
      nextR.map(v=>v._1).foreach(r=>println("next r : ",r.id," ",r.value))
      //consecutive ai = 1, need to remove ri
      if(nextR.size != cur_R.size){
        cur_R = nextR.map(v=>v._1)
        ans = firstSolve(cur_R.map(v=>v.value),k)
      }
      else{
        val anchors = ans.zipWithIndex.filter(v=>v._1<1).map(v=>v._2)
        ans = specialSolve(cur_R.map(v=>v.value),anchors,k)
      }
      counter = counter + 1
    }
    var res = Array.fill(input.size+1)(1.0)
    cur_R.zipWithIndex.map(r=>{
      res(r._1.id) = ans(r._2)
    })
    res
  }
  def main(args: Array[String]) = {
//    val input = Array(26025,306617,26025,306617,2,306617,306617,26025)
    val input = Array(26025,306617,306617,2,306617,26025,26025,306617,306617,306617,332642,613234,613234,306617,306617)
    val ans = solve(input,64)
    ans.foreach(println)
  }
}