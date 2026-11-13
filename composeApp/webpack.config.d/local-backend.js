;(function (config) {
    const url = process.env.TANA_API_BASE_URL;
    const key = process.env.TANA_SUPABASE_KEY;
    if (config.mode !== 'development' || !url || !key) return;
    const webpack = require('webpack');
    config.plugins = config.plugins || [];
    config.plugins.push(new webpack.BannerPlugin({
        raw: true,
        entryOnly: true,
        test: /\.js$/,
        banner: 'window.TANA_API_BASE_URL = ' + JSON.stringify(url) + '; window.TANA_SUPABASE_KEY = ' + JSON.stringify(key) + ';',
    }));
    console.log('[tana] development bundle talks to ' + url);
})(config);
