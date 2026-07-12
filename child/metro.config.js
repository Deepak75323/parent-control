const path = require('path');
const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');

const projectRoot = __dirname;
const sharedRoot = path.resolve(projectRoot, '../shared');

/** Avoid Watchman "Operation not permitted" on macOS Desktop folders */
const config = {
  watchFolders: [sharedRoot],
  watcher: {
    watchman: {
      deferStates: ['hg.update'],
    },
  },
  resolver: {
    useWatchman: false,
    nodeModulesPaths: [path.resolve(projectRoot, 'node_modules')],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
