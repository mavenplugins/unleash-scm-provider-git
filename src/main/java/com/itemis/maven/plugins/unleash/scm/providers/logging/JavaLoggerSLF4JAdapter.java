package com.itemis.maven.plugins.unleash.scm.providers.logging;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.slf4j.LoggerFactory;

public class JavaLoggerSLF4JAdapter extends Logger {
  private org.slf4j.Logger slf4jLogger;

  public static JavaLoggerSLF4JAdapter getLogger(Class<?> clazz) {
    return getLogger(clazz.getName());
  }

  public static JavaLoggerSLF4JAdapter getLogger(String name) {
    return new JavaLoggerSLF4JAdapter(name, LoggerFactory.getLogger(name));
  }

  private JavaLoggerSLF4JAdapter(String name, org.slf4j.Logger delegate) {
    super(name, null);
    this.slf4jLogger = delegate;
  }

  @Override
  public void log(LogRecord record) {
    if (!isLoggable(record.getLevel())) {
      return;
    }

    String message = record.getMessage();
    if (message != null && record.getParameters() != null) {
      MessageFormat formatter = new MessageFormat(message);
      message = formatter.format(record.getParameters());
    }

    if (isDebug(record.getLevel())) {
      this.slf4jLogger.debug(message, record.getThrown());
    } else if (isInfo(record.getLevel())) {
      this.slf4jLogger.info(message, record.getThrown());
    } else if (isWarn(record.getLevel())) {
      this.slf4jLogger.warn(message, record.getThrown());
    } else if (isError(record.getLevel())) {
      this.slf4jLogger.error(message, record.getThrown());
    }
  }

  @Override
  public void log(Level level, String msg) {
    log(new LogRecord(level, msg));
  }

  @Override
  public void log(Level level, String msg, Object param1) {
    LogRecord record = new LogRecord(level, msg);
    record.setParameters(new Object[] { param1 });
    log(record);
  }

  @Override
  public void log(Level level, String msg, Object[] params) {
    LogRecord record = new LogRecord(level, msg);
    record.setParameters(params);
    log(record);
  }

  @Override
  public void log(Level level, String msg, Throwable thrown) {
    LogRecord record = new LogRecord(level, msg);
    record.setThrown(thrown);
    log(record);
  }

  @Override
  public Level getLevel() {
    if (this.slf4jLogger.isDebugEnabled()) {
      return Level.ALL;
    }
    if (this.slf4jLogger.isInfoEnabled()) {
      return Level.INFO;
    }
    if (this.slf4jLogger.isWarnEnabled()) {
      return Level.WARNING;
    }
    if (this.slf4jLogger.isErrorEnabled()) {
      return Level.SEVERE;
    }
    return Level.OFF;
  }

  @Override
  public boolean isLoggable(Level level) {
    if (isDebug(level) && this.slf4jLogger.isDebugEnabled()) {
      return true;
    } else if (isInfo(level) && this.slf4jLogger.isInfoEnabled()) {
      return true;
    } else if (isWarn(level) && this.slf4jLogger.isWarnEnabled()) {
      return true;
    } else if (isError(level) && this.slf4jLogger.isErrorEnabled()) {
      return true;
    }
    return false;
  }

  private boolean isDebug(Level level) {
    return level.intValue() <= Level.CONFIG.intValue();
  }

  private boolean isInfo(Level level) {
    int value = level.intValue();
    return value <= Level.INFO.intValue() && value > Level.CONFIG.intValue();
  }

  private boolean isWarn(Level level) {
    int value = level.intValue();
    return value <= Level.WARNING.intValue() && value > Level.INFO.intValue();
  }

  private boolean isError(Level level) {
    int value = level.intValue();
    return value <= Level.SEVERE.intValue() && value > Level.WARNING.intValue();
  }
}
