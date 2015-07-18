package openclrenderdemo

import scala.reflect.macros.whitebox

object OpenCLMacrosImpl {
  def callAndNullImpl(c: whitebox.Context)(f: c.Tree): c.Tree = {
    import c.universe._
    f match {
      case Apply(m, List(v)) =>
        q"""
         if($v != null){
           $m($v)
           $v = null
         }
       """
    }
  }
}
