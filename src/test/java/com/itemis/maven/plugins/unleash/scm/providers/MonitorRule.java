package com.itemis.maven.plugins.unleash.scm.providers;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;

public class MonitorRule extends TestWatcher {

  private final Logger logger;

  public MonitorRule(Logger logger) {
    super();
    this.logger = logger;
  }

  /**
   * Invoked when a test succeeds
   */
  @Override
  protected void succeeded(Description description) {
    this.logger.info("===== Test {} SUCCEEDED. =====", description.getMethodName());
  }

  /**
   * Invoked when a test fails
   */
  @Override
  protected void failed(Throwable e, Description description) {
    this.logger.error("##### Test {} FAILED with {}. #####", description.getMethodName(), e);
  }

  /**
   * Invoked when a test is skipped due to a failed assumption.
   */
  @Override
  protected void skipped(AssumptionViolatedException e, Description description) {
    this.logger.info("===== Test {} SKIPPED. =====", description.getMethodName());
  }

  /**
   * Invoked when a test is about to start
   */
  @Override
  protected void starting(Description description) {
    this.logger.info("=========================== {} ===========================", description.getMethodName());
    this.logger.info("===== Test {} RUNNING... =====", description.getMethodName());
  }

  /**
   * Invoked when a test method finishes (whether passing or failing)
   */
  @Override
  protected void finished(Description description) {
    this.logger.info("===== Test {} FINISHED. =====", description.getMethodName());
    this.logger
        .info("=================================================================================================");
  }
}