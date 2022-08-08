import scala.io.StdIn.readLine
import scala.collection.mutable.StringBuilder

object ScannerTest {
  def main(args: Array[String]) : Unit = {
    val sb = StringBuilder()    
    var ok = true
    val reg = "\\s*next\\((.*)\\)\\s*:=\\s*(.*);\\s*".r
    while (ok) {
      val ln = readLine()
      ok = ln != null
      if (ok) {
        ln match {
            case reg(lhs,rhs) =>
                sb.append("\tnext(%s) := case\n\t rt_res1 : %s\n\t TRUE : %s;\n".format(lhs,rhs,lhs))
            case _ =>
                sb.append(ln)
                sb.append("\n")
        }
      }
    }
    System.out.println(sb.toString)
  }
}