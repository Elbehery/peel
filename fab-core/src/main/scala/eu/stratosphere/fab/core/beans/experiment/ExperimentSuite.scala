package eu.stratosphere.fab.core.beans.experiment

import java.io.File

import com.typesafe.config._
import eu.stratosphere.fab.core.beans.system.{Lifespan, System}
import eu.stratosphere.fab.core.graph.{DependencyGraph, Node}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.BeanNameAware

class ExperimentSuite(final val experiments: List[Experiment]) extends Node with BeanNameAware {

  final val logger = LoggerFactory.getLogger(this.getClass)

  var name = "experiments"

  def run() = {
    logger.info(s"Running experiments suite '${name}'")
    logger.info(s"Constructing dependency graph for suite")
    val graph = createGraph()

    //TODO check for cycles in the graph
    if (graph.isEmpty)
      throw new RuntimeException("Suite is empty!")

    // SUITE lifespan
    try {
      val baseConfig = loadConfig(graph)

      // update config
      for (n <- graph.vertices) n match {
        case s: System => s.config = baseConfig
        case _ => Unit
      }

      logger.info("Setting up systems with SUITE lifespan")
      for (n <- graph.reverse.traverse()) n match {
        case s: System => if (s.lifespan == Lifespan.SUITE) s.setUp()
        case _ => Unit
      }

      logger.info("Executing experiments in suite")
      for (e <- experiments) {
        // EXPERIMENT lifespan
        try {
          logger.info("#" * 60)
          logger.info("Current experiment is %s".format(e.config.getString("experiment.name.base")))

          // update config
          val expConfig = loadConfig(graph, Some(e))
          for (n <- graph.descendants(e).reverse) n match {
            case s: System => s.config = expConfig
            case _ => Unit
          }

          logger.info("Updating systems with SUITE lifespan")
          for (n <- graph.reverse.traverse()) n match {
            case s: System => if (s.lifespan == Lifespan.SUITE) s.update()
            case _ => Unit
          }

          logger.info("Setting up systems with EXPERIMENT lifespan")
          for (n <- graph.reverse.traverse(); if graph.descendants(e).contains(n)) n match {
            case s: System => if (s.lifespan == Lifespan.EXPERIMENT) s.setUp()
            case _ => Unit
          }

          for (r <- 1 to e.runs) {
            e.config = expConfig
              .withValue("experiment.run", ConfigValueFactory.fromAnyRef(r))
              .withValue("experiment.name.run", ConfigValueFactory.fromAnyRef("%s-run%02d".format(expConfig.getString("experiment.name.base"), r))) // update config
            e.run() // run experiment
            e.config = expConfig // restore original config
          }

        } catch {
          case e: Exception => logger.error(s"Exception of type ${e.getClass} in ExperimentSuite: ${e.getMessage}")
          case _: Throwable => logger.error(s"Exception in ExperimentSuite")

        } finally {
          logger.info("Tearing down systems with EXPERIMENT lifespan")
          for (n <- graph.traverse(); if graph.descendants(e).contains(n)) n match {
            case s: System => if (s.lifespan == Lifespan.EXPERIMENT) s.tearDown()
            case _ => Unit
          }
        }
      }

    }

    catch {
      case e: Exception => logger.error(s"Exception of type ${e.getClass} in ExperimentSuite: ${e.getMessage}")
      case _: Throwable => logger.error(s"Exception in ExperimentSuite")

    } finally {
      logger.info("#" * 60)
      logger.info("Tearing down systems with SUITE lifespan")
      for (n <- graph.traverse()) n match {
        case s: System => if (s.lifespan == Lifespan.SUITE) s.tearDown()
        case _ => Unit
      }
    }
  }

  private def loadConfig(graph: DependencyGraph[Node], exp: Option[Experiment] = None, run: Option[Integer] = None) = {
    // helpers
    val options = ConfigParseOptions.defaults().setClassLoader(this.getClass.getClassLoader)

    // load reference configuration
    var config = ConfigFactory.parseResources("reference.conf", options)

    // load systems configuration
    for (n <- graph.reverse.traverse().reverse) n match {
      case s: System =>
        // load {system.defaultName}.conf
        config = ConfigFactory.parseResources(s"${s.defaultName.toLowerCase}.conf", options).withFallback(config)
        // load {system.name}.conf
        if (s.name.toLowerCase != s.defaultName.toLowerCase)
          config = ConfigFactory.parseFile(new File(s"${System.getProperty("app.path.config")}/${s.name.toLowerCase}.conf"), options).withFallback(config)
      case _ => Unit
    }

    // load application configuration
    config = ConfigFactory.parseFile(new File(s"${System.getProperty("app.path.config")}/application.conf"), options).withFallback(config)

    // load the experiment config
    if (exp.isDefined) config =
      exp.get.config.withFallback(config)

    // load system properties
    config = ConfigFactory.systemProperties.withFallback(config)

    // resolve and return config
    config.resolve()
  }

  /**
   * Create a directed Graph from all Experiments and their dependencies.
   *
   * @return Graph with systems as vertices and dependencies as edges
   */
  private def createGraph(): DependencyGraph[Node] = {

    val g = new DependencyGraph[Node]

    def processDependencies(s: System): Unit = {
      if (s.dependencies.nonEmpty) {
        for (d <- s.dependencies) {
          g.addEdge(s, d)
          processDependencies(d)
        }
      }
    }

    for (e <- experiments) {
      g.addEdge(this, e)
      g.addEdge(e, e.runner)
      processDependencies(e.runner)
    }

    g // return the graph
  }

  override def toString = "Experiment Suite"

  override def setBeanName(n: String): Unit = name = n
}
