# hulunote, a tool for networked thought, think different

## technical framework

* clojure and clojurescript
* datascript + rum
* instaparse
* ring
* clj-http
* re-frame

## develop

* db environment
```bash

> psql
CREATE DATABASE functor_api;

$  psql -d functor_api -Upostgres < ./sql/init.sql
$  for sql in `find . -name "*.sql" | grep -v init.sql | sort `; do psql -d functor_api -Upostgres < $sql ; done

```
* backend
```bash
$ cp config/config.clj.default config/config.clj

$ clojure -A:cider:run
```
* frontend
```bash
$ yarn 

$ clojure -A:cider:cljs:shadow watch hulunote
```

