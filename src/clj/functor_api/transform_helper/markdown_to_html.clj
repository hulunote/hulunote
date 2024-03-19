(ns functor-api.transform-helper.markdown-to-html
  (:require [clojure.string :as strings])
  (:import [org.commonmark.parser Parser]
           [org.commonmark.renderer.html HtmlRenderer]))


(defn hulunote-italics->markdown
  "葫芦笔记里的斜体标志是'__'，而标准markdown的是'*'，这里需要转换一下"
  [markdown]
  (let [markdown (strings/replace markdown "&nbsp;" " ")
        italics (re-seq #"__.+__" markdown)
        replacements (map #(do [%, (strings/replace % "__" "*")]) italics)]
    (reduce #(strings/replace %1 (first %2) (second %2)) markdown replacements)))

(defn hulunote-native->markdown
  "葫芦笔记里的原生<>符号转成《》防止app端的误解析"
  [markdown]
  (if (or (strings/includes? markdown "<")
          (strings/includes? markdown ">"))
    (-> markdown
        (strings/replace "<" "《")
        (strings/replace ">" "》"))
    markdown))

(defn- find-native-links [markdown]
  (loop [idx 0
         acc []]
    (let [ts (strings/index-of markdown "](" idx)
          te (if ts (strings/index-of markdown ")" (inc ts)) nil)
          target (if (and ts te)
                   (subs markdown ts te)
                   nil)]
      (if (= target nil)
        acc
        (recur te (conj acc target))))))

(defn web-hash-navtive->markdown
  "原生链接里的#号和标签的冲突了，这里先改一下，再改回去"
  [markdown]
  (if (strings/includes? markdown "#")
    (let [native-links (find-native-links markdown)]
      (reduce #(let [link %2
                     replacement (strings/replace link "#" "{{hash-tag}}")]
                 (strings/replace %1 link replacement))
              markdown native-links))
    markdown))

(defn before-transfer-html
  "markdown转html前的独立处理"
  [markdown]
  (-> markdown
      (hulunote-italics->markdown)
      (hulunote-native->markdown)
      (web-hash-navtive->markdown)))

;;; app端的各个额外转换

(defn- find-double-link-text [s start]
  (let [ts (strings/index-of s "[[" start)]
    (if (= ts nil)
      [nil ""]
      (let [te (strings/index-of s "]]" (inc ts))]
        (if (= te nil)
          [nil ""]
          [(+ te 2) (subs s ts (+ te 2))])))))

(defn hulunote-double-link->html
  "双链转成app端的html"
  [html]
  (let [first-idx (strings/index-of html "[[")]
    (if (= first-idx nil)
      html
      (loop [idx first-idx
             acc []]
        (let [[new-idx target] (find-double-link-text html idx)]
          (if (= new-idx nil)
            (reduce #(strings/replace %1 (first %2) (second %2))
                    html acc)
            (let [target-text (subs target 2 (- (count target) 2))
                  ;; 对内容进行url编码
                  target-text (java.net.URLEncoder/encode target-text)
                  replacement (str "<a href=\"hulunote-double-link://" target-text "\">" target "</a>")
                  item [target replacement]]
              (recur new-idx (conj acc item)))))))))

(defn- find-tag-text [s start]
  (let [ts (strings/index-of s "#" start)]
    (if (= ts nil)
      [nil ""]
      (let [te1 (strings/index-of s " " (inc ts))  ;; 后面有空格分割
            te2 (strings/index-of s "\n" (inc ts)) ;; 后面直接跟换行
            te3 (strings/index-of s "<" (inc ts))  ;; 后面直接是别的html属性（原生不再有<符号）
            te4 (count s)
            tes (->> [te1 te2 te3 te4]
                     (filter some?))
            te (apply min tes)
            target (subs s ts te)]
        [te target]))))

(defn hulunote-tag->html
  "标签转成app端的html"
  [html]
  (let [first-idx (strings/index-of html "#")]
    (if (= first-idx nil)
      html
      (loop [idx first-idx
             acc []]
        (let [[new-idx target] (find-tag-text html idx)]
          (if (= new-idx nil)
            (reduce #(strings/replace %1 (first %2) (second %2))
                    html acc)
            (let [target-text (strings/trim (subs target 1))
                  ;; 对内容进行url编码
                  target-text (java.net.URLEncoder/encode target-text)
                  replacement (str "<a href=\"hulunote-tag://" target-text "\">" target "</a>")
                  item [target replacement]]
              (recur new-idx (conj acc item)))))))))

(defn hulunote-ref->html
  "块引用转成app端的html"
  [html]
  (let [ref-q (re-seq #"\(\((\w|[^\x00-\xff]|-)+\)\)" html)]
    (if (empty? ref-q)
      html
      (->> ref-q
           (map #(if (vector? %) (first %) %))
           (set)
           (filter #(= (count %) 40))
           (reduce #(let [target %2
                          target-id (subs target 2 38)
                          replacement (str "<a href=\"hulunote-ref://" target-id "\">引用准备中</a>")]
                      (strings/replace %1 target replacement))
                   html)))))

(defn hulunote-todo->html
  "TODO转成app端的html"
  [html]
  (if (or (strings/includes? html "{{[[TODO]]}}")
          (strings/includes? html "{{[[DONE]]}}"))
    (-> html
        (strings/replace "{{[[TODO]]}}" "<img src=\"todo_0.svg\">")
        (strings/replace "{{[[DONE]]}}" "<img src=\"todo_1.svg\">"))
    html))

(defn delete-line->html
  "删除线未能转成html，这里需要单独处理"
  [html]
  (let [strike-q (re-seq #"~~.+~~" html)]
    (if (empty? strike-q)
      html
      (->> strike-q
           (map #(if (vector? %) (first %) %))
           (set)
           (reduce #(let [target %2
                          target-text (subs target 2 (- (count target) 2))
                          replacement (str "<del>" target-text "</del>")]
                      (strings/replace %1 target replacement))
                   html)))))

(defn web-hash-tag-recover
  "经过before修改的#，现在改回来"
  [html]
  (strings/replace html "{{hash-tag}}" "#"))

(defn after-transfer-html
  "原生markdown转换为html之后的转换"
  [html]
  (-> html
      (hulunote-todo->html)
      (hulunote-double-link->html)
      (hulunote-tag->html)
      (hulunote-ref->html)
      (delete-line->html)
      (web-hash-tag-recover)))

(defn markdown->html
  "markdown文本转html文本"
  [markdown]
  (let [tranfered-markdown (before-transfer-html markdown)
        parser (.. Parser (builder) (build))
        document (.parse parser tranfered-markdown)
        renderer (.. HtmlRenderer (builder) (build))
        res (.render renderer document)
        len (count res)
        html (if (strings/ends-with? res "\n")
               (subs res 0 (dec len))
               res)]
    (after-transfer-html html)))
