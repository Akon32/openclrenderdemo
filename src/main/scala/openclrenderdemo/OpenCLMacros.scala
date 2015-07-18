package openclrenderdemo

import scala.language.experimental.macros

object OpenCLMacros {

  def callAndNull(f: Any): Unit = macro OpenCLMacrosImpl.callAndNullImpl
}
