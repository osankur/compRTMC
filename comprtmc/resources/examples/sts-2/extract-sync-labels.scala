import scala.io._
import scala.collection.mutable._
object Main extends App{
    val lines = Source.stdin.getLines.toList
    // Store (edge number, edge guard)
    var go = ("","");
    val poll1 = ArrayBuffer[(String,String)]();
    val poll2 = ArrayBuffer[(String,String)]();
    val cpoll = ArrayBuffer[(String,String)]();
    val tau_reset_ygt2 = ArrayBuffer[(String,String)]();
    val tau_reset_yleq2 = ArrayBuffer[(String,String)]();
    val tau_reset_z = ArrayBuffer[(String,String)]();
    val tau_reset_z1 = ArrayBuffer[(String,String)]();
    val tau_reset_z2 = ArrayBuffer[(String,String)]();
    val tau_reset_yz = ArrayBuffer[(String,String)]();
    val set_drive = ArrayBuffer[(String,String)]();
    val err = ArrayBuffer[(String,String)]()
    val regEdge = "-- _edge_ = ([0-9]*): ([^ ]*) (.*)".r
    for (line <- lines){
        line match {
            case regEdge(no, label, g) =>
                // System.out.println("Processing: " + no + " : " + label + " : " + g)
                if (label.contains("InOut_go")){
                    go = (no,g)
                } else if (label.contains("Ctrl_poll")){
                    cpoll.append((no,g))
                } else if (label.contains("Ctrl_set_drive") || label.contains("Ctrl_set_not_drive") ){
                    set_drive.append((no,g))
                } else if (label.contains("A1_poll")){
                    poll1.append((no,g))
                } else if (label.contains("A2_poll")){
                    poll2.append((no,g))
                } else if (label.contains("tau_reset_z1")){
                    tau_reset_z1.append((no,g))
                } else if (label.contains("tau_reset_z2")){
                    tau_reset_z2.append((no,g))
                } else if (label.contains("Ctrl_tau_reset_z")){
                    tau_reset_z.append((no,g));
                } else if (label.contains("Ctrl_tau_reset_yz")){
                    tau_reset_yz.append((no,g));
                } else if (label.contains("Ctrl_tau_yleq2")){
                    tau_reset_yleq2.append((no,g))
                } else if (label.contains("Ctrl_tau_ygt2")){
                    tau_reset_ygt2.append((no,g))
                } else if (label.contains("_err_")){
                    err.append((no,g))
                } else {
                    System.err.println("Ignoring edge " + no + ": " + label)
                }
            case _ => ()
        }
    }
    //System.out.println("\t_go_ := (_edge_ = %s);".format(go._1,go._2))
    val printSync = { (pair : (String,String)) => "(_edge_ = " + pair._1 + "& " + pair._2 + ") "}
    System.out.println("\t_rt_pollone := %s;\n".format(poll1.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_polltwo := %s;".format(poll2.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_cpoll := %s;".format(cpoll.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_tau_ygttwo := %s;".format(tau_reset_ygt2.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_tau_yleqtwo := %s;".format(tau_reset_yleq2.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_tau_reset_yz := %s;".format(tau_reset_yz.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_tau_reset_z := %s;".format(tau_reset_z.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_tau_reset_zone := %s;".format(tau_reset_z1.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_tau_reset_ztwo := %s;".format(tau_reset_z2.map(printSync).mkString(" | ")))
    System.out.println("\t_rt_set_drive := %s;".format(set_drive.map(printSync).mkString(" | ")))
    System.out.println("\tto_err := %s;".format(err.map(printSync).mkString(" | ")))
}