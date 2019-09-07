package com.tzahk;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.nio.file.WatchKey;

public final class ConfigDirectory {
    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static WatchService configWatcher;
    private static WatchKey configWatchKey;

    public static void watchConfigFilesForChanges() {
        if (null == configWatchKey) {
            createConfigFileWatch();
        }

        pollForConfigFileChanges();
    }

    private static void createConfigFileWatch() {
        if (configWatchKey != null) {
            return;
        }

        try {
            final FileSystem fs = FileSystems.getDefault();
            configWatcher = fs.newWatchService();
            final Path etcConfig = fs.getPath("etc", "config");
            configWatchKey = etcConfig.register(
                configWatcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException ex) {
            log.error("failed to create FileWatcher", ex);
        }
    }

    private static void pollForConfigFileChanges() {
        if (null == configWatchKey) {
            return;
        }

        for (final WatchEvent<?> event : configWatchKey.pollEvents()) {
            final WatchEvent<Path> pathEvent = (WatchEvent<Path>)event;
            final Path path = pathEvent.context();
            final String changedConfigKey = path.toFile().getName();
            final String changedConfigValue = readConfigFile(path);
            // TODO: Do something with the changed key-value pair!
        }
    }

    private static String readConfigFile(Path configFile) {
        try {
            return Files.readString(configFile);
        } catch (IOException ex) {
            log.error("failed to read config file " + configFile, ex);
            return null;
        }
    }    
}
