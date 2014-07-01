package eu.stratosphere.fab.core.beans.data

import com.typesafe.config.ConfigFactory
import eu.stratosphere.fab.core.beans.system.System
import eu.stratosphere.fab.core.config.Configurable
import eu.stratosphere.fab.core.graph.Node
import org.slf4j.LoggerFactory

abstract class DataSet(val path: String, val dependencies: Set[System]) extends Node with Configurable {

  import scala.language.implicitConversions

  final val logger = LoggerFactory.getLogger(this.getClass)

  override var config = ConfigFactory.empty()

  /**
   * Create the data set represented by this bean.
   */
  def materialize(): Unit

  /**
   * Alias of name.
   *
   * @return
   */
  override def toString: String = resolve(path)
}
