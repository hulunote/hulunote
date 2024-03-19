(ns functor-api.transform-helper.html-to-markdown
  (:require [pl.danieljanus.tagsoup :as html-parser]
            [clojure.string :as strings]
            [functor-api.util :as u]
            [clj-http.client :as client]
            [clojure.core.async :as async]))

(defn find-body-tag
  "查找dom的body(default)"
  [data]
  (let [[tag _attributes & children] data]
    (if (= tag :body)
      data
      (loop [doms children]
        (if (empty? doms)
          nil
          (let [child (first doms)]
            (if (= (type child) java.lang.String)
              (recur (rest doms))
              (if-let [found (find-body-tag child)]
                found
                (recur (rest doms))))))))))

(defn find-tag-by-id
  "根据dom的id查找"
  [data id]
  (let [[_tag attributes & children] data]
    (if (= (:id attributes) id)
      data
      (loop [doms children]
        (if (empty? doms)
          nil
          (let [child (first doms)]
            (if (= (type child) java.lang.String)
              (recur (rest doms))
              (if-let [found (find-tag-by-id child id)]
                found
                (recur (rest doms))))))))))

(declare
 docs-to-nav-content
 docs-back-to-html
 pure-single-docs->md*
 block-tag->md*)

;; 只为转最基本的字符串
(defn children->string [children]
  (if (empty? children)
    ""
    (let [child (first children)]
      (if (= (type child) java.lang.String)
        child
        (recur (rest children))))))

(def h*map {:h1 "#"
            :h2 "##"
            :h3 "###"
            :h4 "####"
            :h5 "#####"
            :h6 "######"})

(defn- download-file-and-upload->s3
  "会卡死进程的程序: 下载保存到本地"
  [url file-name mime-type]
  (u/log-todo "转html为markdown，下载文件")
  #_(let [stream (:body (client/get url {:as :stream}))] 
    (file/update-local-file-stream->s3 stream file-name mime-type)))

;; 变回html 
(defn doc-back-to-html [doc]
  (if (= (type doc) java.lang.String)
    doc
    (let [[tag attributes & children] doc
          children-s (docs-back-to-html children)
          attributes-s (reduce #(let [key (first %2)
                                      value (second %2)]
                                  (str " " key "=" value))
                               "" (seq attributes))
          tag-s (name tag)]
      (str "<" tag-s attributes-s ">"
           children-s
           "</" tag-s ">"))))

(defn docs-back-to-html [children]
  (let [htmls (map #(doc-back-to-html %) children)]
    (strings/join htmls)))

(defn <a>->hulunote-double-link [href children]
  (let [text (children->string children)
        link (strings/replace href "hulunote-double-link://" "")
        link (java.net.URLDecoder/decode link)]
    (if (and (strings/starts-with? text "[[")
             (strings/ends-with? text "]]"))
      text
      (str "[[" link "]]"))))

(defn <a>->hulunote-tag [href children]
  (let [text (children->string children)
        link (strings/replace href "hulunote-tag://" "")
        link (java.net.URLDecoder/decode link)]
    (if (strings/starts-with? text "#")
      text
      (str "#" link))))

(defn <a>->hulunote-ref [href]
  (let [nav-id (strings/replace href "hulunote-ref://" "")]
    (str "((" nav-id "))")))

;; 链接
(defn <a>->md* [{:keys [href]} children]
  (cond
    (strings/starts-with? href "hulunote-double-link://")
    (<a>->hulunote-double-link href children)

    (strings/starts-with? href "hulunote-tag://")
    (<a>->hulunote-tag href children)

    (strings/starts-with? href "hulunote-ref://")
    (<a>->hulunote-ref href)

    :else
    (let [text (children->string children)]
      (str "[" text "](" href ")"))))

;; 黑体
(defn <b>->md* [children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)]
    (cond
      (empty? text)
      ""
      (and (strings/starts-with? text "**")
           (strings/ends-with? text "**"))
      text
      :else (str "**" text "**"))))

;; 换行
(defn <br>->md* []
  "\n")

;; 分割线 
(defn <hr>->md* []
  "---")

;; 强调文本，斜体加粗
(defn <em>->md* [children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)]
    (cond
      (empty? text)
      ""
      (and (strings/starts-with? text "***")
           (strings/ends-with? text "***"))
      text
      :else (str "***" text "***"))))

;; 标题
(defn <h*>->md* [tag children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)
        prefix (get h*map tag)]
    (str prefix text)))

;; 斜体
(defn <i>->md* [children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)]
    (cond
      (empty? text)
      ""
      (and (strings/starts-with? text "__")
           (strings/ends-with? text "__"))
      text
      :else (str "__" text "__"))))

(defn <img>->hulunote-todo [src]
  (if (= src "todo_0.svg")
    "{{[[TODO]]}}"
    "{{[[DONE]]}}"))

;; 图片，不转存
(defn <img>->md* [{:keys [src data-src]} children]
  (if (or (= src "todo_0.svg")
          (= src "todo_1.svg"))
    (<img>->hulunote-todo src)
    (let [text (children->string children)
          real-src (if-not (empty? src)
                     src
                     data-src)]
      (str "![" text "](" real-src ")"))))

;; 图片，转存 
(defn <img>->md*! [{:keys [src data-src data-type]} children]
  (if (or (= src "todo_0.svg")
          (= src "todo_1.svg"))
    (<img>->hulunote-todo src)
    (let [text (children->string children)
          real-src (if-not (empty? src)
                     src
                     data-src)
          file-name (u/gen-token)
          new-url #_(file/get-s3-file-url file-name) "TODO://get-image-path"
          mime-type (case data-type
                      "svg" "image/svg+xml"
                      "png" "image/png"
                      "jpg" "image/jpg"
                      nil)]
      (async/thread (download-file-and-upload->s3 real-src file-name mime-type))
      (u/log-todo "转html为markdown，获取url")
      (str "![" text "](" new-url ")"))))

;; 段落
(defn <p>->md* [children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)]
    (str text "\n")))

;; 删除线文本 
(defn <strike>->md* [children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)]
    (cond
      (empty? text)
      ""
      (and (strings/starts-with? text "~~")
           (strings/ends-with? text "~~"))
      text
      :else (str "~~" text "~~"))))

;; 加粗文本
(defn <strong>->md* [children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)]
    (cond
      (empty? text)
      ""
      (and (strings/starts-with? text "**")
           (strings/ends-with? text "**"))
      text
      :else (str "**" text "**"))))

;; 代码
(defn <code>->md* [{cls :class} children resave-image?]
  (let [text (pure-single-docs->md* children resave-image?)]
    (if (strings/includes? text "\n")
      ;;多行代码
      (str "```" cls "\n"
           text
           "```")
      ;;单行
      (str "`" text "`"))))

;; pre 没有特别的转换
(defn <pre>->md* [children resave-image?]
  (pure-single-docs->md* children resave-image?))

;; span 没有特别的转换
;; 判断是否是Android的删除线
(defn <span>->md* [{:keys [style]} children resave-image?]
  (if (= style "text-decoration:line-through;")
    (<strike>->md* children resave-image?)
    (pure-single-docs->md* children resave-image?)))

;; ul 这个节点比较特殊，加特殊的前缀符号
(defn <ul>->md* [children resave-image?]
  (let [prefix "!{{ul}}!:"
        text (pure-single-docs->md* children resave-image?)]
    (str prefix text)))

;; li 也是特殊节点，加md的前缀
(defn <li>->md* [children resave-image?]
  (let [prefix " * "
        text (-> (pure-single-docs->md* children resave-image?)
                 (strings/replace "!{{block-div-section}}!:!{{<inner-block>}}!" " ")
                 (strings/replace "!{{<inner-block>}}!" "\n"))]
    (str prefix text)))

;; svg，html上的特殊节点，用来表现各种图案的，这里不解析
(defn <svg>->md* []
  "")

(defn <div>or<section>->md* [children resave-image?]
  (let [prefix "!{{block-div-section}}!:"
        nodes (block-tag->md* children resave-image? [])
        text (reduce #(let [item (str "!{{<inner-block>}}!" %2)]
                        (str %1 item)) "" nodes)]
    (str prefix text)))

;; 不支持的tag
(defn unsupported->md* [doc]
  (let [text (doc-back-to-html doc)]
    (str "```html\n" text "\n```")))

(defn pure-single-doc->md*
  "纯单体节点的转换"
  [doc resave-image?]
  (if (= (type doc) java.lang.String)
    doc
    (let [[tag attributes & children] doc]
      (cond
        (= tag :a) (<a>->md* attributes children)
        (= tag :b) (<b>->md* children resave-image?)
        (= tag :br) (<br>->md*)
        (= tag :hr) (<hr>->md*)
        (= tag :em) (<i>->md* children resave-image?)
        (u/in? tag [:h1 :h2 :h3 :h4 :h5 :h6]) (<h*>->md* tag children resave-image?)
        (= tag :i) (<i>->md* children resave-image?)
        (= tag :img) (if resave-image?
                       (<img>->md*! attributes children)
                       (<img>->md* attributes children))
        (= tag :u) (<p>->md* children resave-image?)
        (= tag :p) (<p>->md* children resave-image?)
        (= tag :strike) (<strike>->md* children resave-image?)
        (= tag :del) (<strike>->md* children resave-image?)
        (= tag :strong) (<strong>->md* children resave-image?)
        (= tag :code) (<code>->md* attributes children resave-image?)
        (= tag :pre) (<pre>->md* children resave-image?)
        (= tag :span) (<span>->md* attributes children resave-image?)
        (or (= tag :ul)
            (= tag :ol)) (<ul>->md* children resave-image?)
        (= tag :li) (<li>->md* children resave-image?)
        (= tag :svg) (<svg>->md*)
        ;; 常规单体节点中遇到块节点
        (or (= tag :div)
            (= tag :section)) (<div>or<section>->md* children resave-image?)
        :else (unsupported->md* doc)))))

(defn pure-single-docs->md*
  "多个纯单体节点的转换"
  [docs resave-image?]
  (let [contents (map #(pure-single-doc->md* % resave-image?) docs)]
    (-> (strings/join contents)
        (strings/replace #"\u00A0" " ")
        (strings/trim))))

;; 块
(defn block-tag->md*
  [children resave-image? acc]
  (if (empty? children)
    acc
    (let [doc (first children)]
      (if (= (type doc) java.lang.String)
        (recur (rest children)
               resave-image?
               (conj acc doc))
        (let [[tag _attributes & schildren] doc]
          (if (or (= tag :div)
                  (= tag :section))
            (let [nested-res (block-tag->md* schildren resave-image? [])]
              (recur (rest children)
                     resave-image?
                     (into acc nested-res)))
            (let [single-res (pure-single-doc->md* doc resave-image?)]
              (if (u/in? single-res [" \n", "\n", "\n\n", "", " "])
                (recur (rest children) resave-image? acc)
                (recur (rest children)
                       resave-image?
                       (conj acc single-res))))))))))

(defn main-doc->mds*
  "html的tag信息转成nav的内容（markdown）列表"
  [doc resave-image?]
  (if (= (type doc) java.lang.String)
    [doc]
    (let [[tag _attributes & children] doc]
      (if (or (= tag :body)
              (= tag :div)
              (= tag :section))
        (block-tag->md* children resave-image? [])
        [(unsupported->md* doc)]))))

(defn html->markdown-nodes
  "html文本转markdown文本列表"
  ([html] (html->markdown-nodes html {}))
  ([html options]
   (let [id (:id options)
         resave-image? (:resave-image? options)
         all (html-parser/parse-string html)
         doc (if id
               (find-tag-by-id all id)
               (find-body-tag all))]
     (main-doc->mds* doc resave-image?))))

(defn html-url->markdown-nodes
  "请求url后转为markdown文本列表"
  ([url] (html-url->markdown-nodes url {}))
  ([url options]
   (let [id (:id options)
         resave-image? (:resave-image? options)
         all (html-parser/parse url)
         doc (if id
               (find-tag-by-id all id)
               (find-body-tag all))]
     (main-doc->mds* doc resave-image?))))

(defn html->doc
  "html文本转成结构化的数据"
  ([html] (html->doc html nil))
  ([html id]
   (let [all (html-parser/parse-string html)]
     (if id
       (find-tag-by-id all id)
       (find-body-tag all)))))

(defn html-url->doc
  "请求url后转为结构化的数据"
  ([html] (html-url->doc html nil))
  ([html id]
   (let [all (html-parser/parse html)]
     (if id
       (find-tag-by-id all id)
       (find-body-tag all)))))
