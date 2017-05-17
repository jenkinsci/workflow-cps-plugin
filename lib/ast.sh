#!/bin/bash
exec groovy -e 'groovy.inspect.swingui.AstBrowser.main(args)' "$@"
