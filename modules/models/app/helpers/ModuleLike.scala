package helpers

import play.api.i18n._

/**
 * @author zepeng.li@gmail.com
 */
trait ModuleLike extends Logging {
  self =>

  /**
   * if empty specified then leave package name as full module name
   *
   * @return module name
   */
  def moduleName: String = ""

  def fullModuleName: String =
    self.getClass.getPackage.getName +
      (if (moduleName.isEmpty) "" else s".$moduleName")

  override def loggerName = fullModuleName

  def msg(key: String, args: Any*)(implicit messages: Messages) = {
    messages(msg_key(key), args: _*)
  }

  def msg_key(key: String) = s"$fullModuleName.$key"
}