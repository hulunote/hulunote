#!/bin/bash

export PATH="/usr/local/opt/openjdk@8/bin:$PATH"
export CPPFLAGS="-I/usr/local/opt/openjdk@8/include"

clojure -A:cljs -M -m shadow.cljs.devtools.cli watch hulunote

# eletron dev:
npx shadow-cljs watch hulunote => cd electron &&   npm run start:dev

