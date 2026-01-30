#!/bin/bash

# 1. 清除 shadow-cljs 编译缓存
rm -rf .shadow-cljs/

# 2. 清除 electron 构建产物
rm -rf electron/app/ electron/dist/

cp shadow-cljs-electron.edn shadow-cljs.edn ## :output-dir "resources/public/hulunote"

# 3. 重新编译
npx shadow-cljs release hulunote

# 4. 验证是否还有 "Chat Database"
grep "Chat Database" ./resources/public/hulunote/hulunote.js

# 5. 如果没有了，重新运行构建脚本
./build_electron.sh

