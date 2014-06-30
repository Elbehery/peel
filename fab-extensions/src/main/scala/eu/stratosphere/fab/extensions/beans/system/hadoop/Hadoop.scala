package eu.stratosphere.fab.extensions.beans.system.hadoop

import eu.stratosphere.fab.core.beans.system.{ExperimentRunner, System}
import eu.stratosphere.fab.core.beans.system.Lifespan.Lifespan
import java.io.File
import eu.stratosphere.fab.core.beans.Shell

class Hadoop(lifespan: Lifespan, dependencies: Set[System] = Set()) extends ExperimentRunner(lifespan, dependencies) {

  val home: String = config.getString("paths.hadoop.v1.home")

  def setUp(): Unit = {
    logger.info("Starting system %s...".format(toString))
    Shell.execute(home + "bin/start-mapred.sh", true)
  }

  def tearDown(): Unit = {
    logger.info("Tearing down system %s...".format(toString))
    Shell.execute(home + "bin/stop-mapred.sh", true)
  }

  def update(): Unit = {

  }

  def run(job: String, input: List[File], output: File) = {
    logger.info("Running Hadoop Job %s %s %s".format(job, input.mkString(" "), output))
    Shell.execute(home + "bin/hadoop jar %s %s %s".format(job, input.mkString(" "), output), true)
  }

  override def toString: String = "Hadoop v1"
}
