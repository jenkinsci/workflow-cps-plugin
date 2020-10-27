const webpack = require('webpack');
const path = require('path');
const { CleanWebpackPlugin: CleanPlugin } = require('clean-webpack-plugin');

module.exports = {
  mode: 'development',
  entry: {
    "workflow-editor": [path.join(__dirname, 'src/main/js/workflow-editor.js')],
  },
  output: {
    path: path.join(
      __dirname,
      'target/generated-resources/adjuncts/org/jenkinsci/plugins/workflow/cps'
    ),
  },
  plugins: [
    new CleanPlugin(),
  ],
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        loader: 'babel-loader',
      },
    ],
  },
}
