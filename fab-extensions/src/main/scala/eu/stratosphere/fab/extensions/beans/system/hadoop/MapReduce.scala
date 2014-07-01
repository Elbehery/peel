package eu.stratosphere.fab.extensions.beans.system.hadoop

import java.io.File
import java.nio.file.{Paths, Files}

import com.samskivert.mustache.Mustache
import eu.stratosphere.fab.core.beans.system.Lifespan.Lifespan
import eu.stratosphere.fab.core.beans.system.{ExperimentRunner, System}
import eu.stratosphere.fab.core.config.{Model, SystemConfig}
import eu.stratosphere.fab.core.util.shell

class MapReduce(lifespan: Lifespan, dependencies: Set[System] = Set(), mc: Mustache.Compiler) extends ExperimentRunner("MapReduce", lifespan, dependencies, mc) {

  override def setUp(): Unit = {
    logger.info(s"Starting system '$toString'...")

    if (config.hasPath("system.hadoop.path.archive")) {
      if (!Files.exists(Paths.get(config.getString("system.hadoop.path.home")))) {
        logger.info(s"Extracting archive ${config.getString("system.hadoop.path.archive.src")} to ${config.getString("system.hadoop.path.archive.dst")}")
        shell.untar(config.getString("system.hadoop.path.archive.src"), config.getString("system.hadoop.path.archive.dst"))

        logger.info(s"Changing owner of ${config.getString("system.hadoop.path.home")} to ${config.getString("system.hadoop.user")}:${config.getString("system.hadoop.group")}")
        shell ! "chown -R %s:%s %s".format(
          config.getString("system.hadoop.user"),
          config.getString("system.hadoop.group"),
          config.getString("system.hadoop.path.home"))
      }
    }

    logger.info(s"Checking system configuration")
    configuration().update()

    shell ! s"${config.getString("system.hadoop.path.home")}/bin/start-mapred.sh"
    logger.info(s"Waiting for all tasktrackers to start")
    waitUntilAllTaskTrackersRunning()
    logger.info(s"System '$toString' is now running")
  }

  override def tearDown(): Unit = {
    logger.info(s"Tearing down system '$toString'")

    shell ! s"${config.getString("system.hadoop.path.home")}/bin/stop-mapred.sh"
  }

  override def update(): Unit = {
    logger.info(s"Checking system configuration of '$toString'")

    val c = configuration()
    if (c.hasChanged) {
      logger.info(s"Configuration changed, restarting '$toString'...")
      shell ! s"${config.getString("system.hadoop.path.home")}/bin/stop-mapred.sh"

      c.update()

      shell ! s"${config.getString("system.hadoop.path.home")}/bin/start-mapred.sh"
      logger.info(s"Waiting for all tasktrackers to start")
      waitUntilAllTaskTrackersRunning()
      logger.info(s"System '$toString' is now running")
    }
  }

  override def run(job: String, input: List[File], output: File) = {
    logger.info("Running MapReduce job")
  }

  override def configuration() = SystemConfig(config, List(
    SystemConfig.Entry[Model.Hosts]("system.hadoop.config.masters",
      "%s/masters".format(config.getString("system.hadoop.path.config")),
      "/templates/hadoop/conf/hosts.mustache", mc),
    SystemConfig.Entry[Model.Hosts]("system.hadoop.config.slaves",
      "%s/slaves".format(config.getString("system.hadoop.path.config")),
      "/templates/hadoop/conf/hosts.mustache", mc),
    SystemConfig.Entry[Model.Env]("system.hadoop.config.env",
      "%s/hadoop-env.sh".format(config.getString("system.hadoop.path.config")),
      "/templates/hadoop/conf/hadoop-env.sh.mustache", mc),
    SystemConfig.Entry[Model.Site]("system.hadoop.config.core",
      "%s/core-site.xml".format(config.getString("system.hadoop.path.config")),
      "/templates/hadoop/conf/site.xml.mustache", mc),
    SystemConfig.Entry[Model.Site]("system.hadoop.config.mapred",
      "%s/mapred-site.xml".format(config.getString("system.hadoop.path.config")),
      "/templates/hadoop/conf/site.xml.mustache", mc)
  ))

  private def waitUntilAllTaskTrackersRunning(): Unit = {
    val user = config.getString("system.hadoop.user")
    val logDir = config.getString("system.hadoop.path.log")

    val totl = config.getStringList("system.hadoop.config.slaves").size()
    val init = Integer.parseInt((shell !! s"""cat $logDir/hadoop-$user-jobtracker-*.log | grep 'Adding a new node:' | wc -l""").trim())
    var curr = init
    while (curr - init < totl) {
      Thread.sleep(1000)
      curr = Integer.parseInt((shell !! s"""cat $logDir/hadoop-$user-jobtracker-*.log | grep 'Adding a new node:' | wc -l""").trim())
    } // TODO: don't loop to infinity
  }
}
