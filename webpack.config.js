const webpack = require('webpack');
const path = require('path');
const MiniCSSExtractPlugin = require('mini-css-extract-plugin');
const { CleanWebpackPlugin: CleanPlugin } = require('clean-webpack-plugin');

module.exports = {
  mode: 'development',
  entry: {
    "workflow-editor": [
      path.join(__dirname, 'src/main/js/workflow-editor.js'),
      path.join(__dirname, 'src/main/less/workflow-editor.less'),
    ],
  },
  output: {
    path: path.join(
      __dirname,
      'target/generated-resources/adjuncts/org/jenkinsci/plugins/workflow/cps'
    ),
  },
  plugins: [
    new MiniCSSExtractPlugin({
      filename: "[name].css",
    }),
    new CleanPlugin(),
  ],
  module: {
    rules: [
      {
        test: /\.(css|less)$/,
        use: [ MiniCSSExtractPlugin.loader, "css-loader", "postcss-loader", "less-loader"]
      },
      {
        test: /\.js$/,
        exclude: /node_modules/,
        loader: 'babel-loader',
      },
    ],
  },
}
