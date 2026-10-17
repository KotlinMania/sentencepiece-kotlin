try {
    module.exports = require('./build/Release/sentencepiece_native.node');
} catch (err) {
    try {
        module.exports = require('./build/Debug/sentencepiece_native.node');
    } catch (err2) {
        throw new Error(
            'Could not load sentencepiece_native addon. ' +
            'Run `npm install` or `node-gyp rebuild` in native/node-sentencepiece directory.\n' +
            'Original error: ' + err.message + '\n' +
            'Debug error: ' + err2.message
        );
    }
}
