#!/bin/bash

export PATH="/usr/local/opt/openjdk@8/bin:$PATH"
export CPPFLAGS="-I/usr/local/opt/openjdk@8/include"

clojure -A:cljs -M -m shadow.cljs.devtools.cli watch hulunote

# eletron dev: cp shadow-cljs-electron.edn shadow-cljs.edn && cd ~/CljPro/hulunote-rust && cargo run
# npx shadow-cljs watch hulunote => cd electron &&   npm run start:dev

# web dev: cp shadow-cljs-web-dev.edn shadow-cljs.edn  && cd ~/CljPro/hulunote-rust && cargo run
# 13 ;;(goog-define API_BASE_URL "https://www.hulunote.top")
# 14 (goog-define API_BASE_URL "http://127.0.0.1:6689")

