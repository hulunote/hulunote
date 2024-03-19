(ns functor-api.transform-helper.core
  (:require [functor-api.transform-helper.html-to-markdown :as h2m]
            [functor-api.transform-helper.markdown-to-html :as m2h]
            [functor-api.transform-helper.note-to-markdown :as n2m]
            [clojure.string :as strings]))


(defn markdown->html
  "markdown文本转html文本"
  [markdown]
  (m2h/markdown->html markdown))

(defn html->markdown
  "html的tags信息转成nav的内容
   options:
   :id string 根据id查找的dom
   :resave-image? boolean 是否对图片进行转存"
  ([html]
   (html->markdown html {}))
  ([html options]
   (-> html
       (strings/replace "&#160;" "{{$blank-space}}")
       (strings/replace "&nbsp;" "{{$blank-space}}")
       (h2m/html->markdown-nodes options)
       (strings/join)
       (strings/trimr)
       (strings/replace "{{$blank-space}}" " "))))

(defn html-url->markdown
  "请求url后html的tags信息转成nav的内容
   options:
   :id string 根据id查找的dom
   :resave-image? boolean 是否对图片进行转存"
  ([html]
   (html-url->markdown html {}))
  ([html options]
   (-> (h2m/html-url->markdown-nodes html options)
       (strings/join)
       (strings/trimr))))

(defn html->markdown-nodes
  "与html->markdown类似，不同的是本函数是获取每个markdown节点，而不是整个文章"
  ([html]
   (html->markdown-nodes html {}))
  ([html options]
   (h2m/html->markdown-nodes html options)))

(defn html-url->markdown-nodes
  "与html-url->markdown类似，不同的是本函数是获取每个markdown节点，而不是整个文章"
  ([html]
   (html-url->markdown-nodes html {}))
  ([html options]
   (h2m/html-url->markdown-nodes html options)))

(defn note->markdown
  "笔记转为markdown"
  [note-id]
  (n2m/note->markdown note-id))

#_(defn note->markdown->view
  "笔记转成完整的文章markdown，可以查看的类型"
  ([note-id] (n2m/note->markdown->view note-id {}))
  ([note-id replacement-map] (n2m/note->markdown->view note-id replacement-map)))
