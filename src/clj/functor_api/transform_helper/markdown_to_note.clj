(ns functor-api.transform-helper.markdown-to-note
  (:require [clojure.string :as strings]
            [functor-api.util :as u]
            [functor-api.transform-helper.markdown-to-html :as m2h]
            [functor-api.transform-helper.util :as helper-util]
            [pl.danieljanus.tagsoup :as html-parser]))


(declare <ul>->navs
         node-children->content)

;; 只为转最基本的字符串
(defn- node-children->string [children]
  (if (empty? children)
    ""
    (let [child (first children)]
      (if (= (type child) java.lang.String)
        child
        (recur (rest children))))))

(defn- <strong>->content [children]
  (let [content (node-children->content children)]
    (cond
      (empty? content) ""
      (and (strings/starts-with? content "**")
           (strings/ends-with? content "**")) content
      :else (str "**" content "**"))))

(defn- <em>->content [children]
  (let [content (node-children->content children)]
    (cond
      (empty? content) ""
      (and (strings/starts-with? content "***")
           (strings/ends-with? content "***")) content
      :else (str "***" content "***"))))

(defn- <i>->content [children]
  (let [content (node-children->content children)]
    (cond
      (empty? content) ""
      (and (strings/starts-with? content "__")
           (strings/ends-with? content "__")) content
      :else (str "__" content "__"))))

(defn- <del>->content [children]
  (let [content (node-children->content children)]
    (cond
      (empty? content) ""
      (and (strings/starts-with? content "~~")
           (strings/ends-with? content "~~")) content
      :else (str "~~" content "~~"))))

(defn- <img>->content [{:keys [src data-src]} children]
  (let [text (node-children->string children)
        real-src (if-not (empty? src) src data-src)]
    (str "![" text "](" real-src ")")))

(defn- <a>->content [{:keys [href]} children]
  (let [text (node-children->string children)]
    (str "[" text "](" href ")")))

(defn- <code>->content [{cls :class} children]
  (let [text (node-children->content children)]
    (if cls
      (str "```" cls "\n" text "```")
      (str "`" text "`"))))

(defn- node->content [node]
  (if (= (type node) java.lang.String)
    node
    (let [[tag attr & children] node]
      (case tag
        :strong (<strong>->content children)
        :b (<strong>->content children)
        :em (<i>->content children)
        :i (<i>->content children)
        :del (<del>->content children)
        :img (<img>->content attr children)
        :a (<a>->content attr children)
        :code (<code>->content attr children)
        :p (node-children->content children)
        (node-children->string children)))))

(defn- node-children->content [children]
  (let [contents (map #(node->content %) children)]
    (-> (strings/join contents)
        (strings/replace #"\u00A0" " ")
        (strings/trim))))

(defn- text->nav [account-id note-id state text]
  (helper-util/create-note-nav! account-id note-id (:parid state) text)
  state)

(defn- <h*>->nav [account-id note-id state heading children]
  (let [parid (nth (:heading-parids state) (dec heading))
        content (node-children->content children)
        id (helper-util/create-note-nav! account-id note-id parid content)
        new-headings (assoc (:heading-parids state) heading id)]
    (assoc state
           :parid id
           :heading-parids new-headings)))

(defn- <p>->nav [account-id note-id state children]
  (let [content (node-children->content children)]
    (helper-util/create-note-nav! account-id note-id (:parid state) content)
    state))

(defn- <blockquote>->nav [account-id note-id state children]
  (let [content (str "> " (node-children->content children))]
    (helper-util/create-note-nav! account-id note-id (:parid state) content)
    state))

(defn- <li>->navs [account-id note-id state node]
  (let [[_tag _attr & children] node]
    (if (= (count children) 1)
      ;; li只有一个节点
      (let [content (node-children->content children)]
        (helper-util/create-note-nav! account-id note-id (:parid state) content))

      ;; li下不止一个节点
      (let [last-node (last children)
            rest-children (take (dec (count children)) children)]
        (if (= (type node) java.lang.String)
          ;; 最后一个是纯文本节点，正常处理所有节点
          (let [content (node-children->content children)]
            (helper-util/create-note-nav! account-id note-id (:parid state) content))
          ;; 是节点，要判断是否是ul节点
          (let [[last-tag _attr & last-children] last-node]
            (if (or (= last-tag :ul) (= last-node :ol))
              ;; 嵌套的ul
              (let [self-content (node-children->content rest-children)
                    self-id (helper-util/create-note-nav! account-id note-id (:parid state) self-content)
                    new-state (assoc state :parid self-id)]
                (<ul>->navs account-id note-id new-state last-children))
              ;; 没有嵌套，全部正常处理
              (let [content (node-children->content children)]
                (helper-util/create-note-nav! account-id note-id (:parid state) content)))))))))

(defn- <ul>->navs [account-id note-id state children]
  (doseq [child children]
    (<li>->navs account-id note-id state child))
  state)

(defn- <tr>->node [account-id note-id parid row]
  (let [[_tr _attr & children] row]
    (loop [cols children
           pid parid]
      (when-not (empty? cols)
        (let [col (first cols)
              [_th _attr & children] col
              content (node-children->content children)
              col-id (helper-util/create-note-nav! account-id note-id pid content)]
          (recur (rest cols) col-id))))))

(defn- <table>->navs [account-id note-id state children]
  (let [[_thead _attributes & trs] (first children)
        head (first trs)
        [_tbody _attributes & trs] (second children)
        rows (concat [head] trs)
        root-id (helper-util/create-note-nav! account-id note-id (:parid state) "{{[[table]]}}")]
    (doseq [row rows]
      (<tr>->node account-id note-id root-id row))))

(defn- <pre>->nav [account-id note-id state children]
  (let [content (node-children->content children)]
    (helper-util/create-note-nav! account-id note-id (:parid state) content)
    state))

(defn- unknown->nav [account-id note-id state children]
  (let [text (node-children->string children)]
    (helper-util/create-note-nav! account-id note-id (:parid state) text)
    state))

(defn- html-node-to-navs! [account-id note-id state node]
  (if (= (type node) java.lang.String)
    ;; 纯文本，直接存
    (text->nav account-id note-id state node)
    ;; html数据节点 
    (let [[tag _attrs & children] node]
      (case tag
        :h1 (<h*>->nav account-id note-id state 1 children)
        :h2 (<h*>->nav account-id note-id state 2 children)
        :h3 (<h*>->nav account-id note-id state 3 children)
        :h4 (<h*>->nav account-id note-id state 4 children)
        :h5 (<h*>->nav account-id note-id state 5 children)
        :h6 (<h*>->nav account-id note-id state 6 children)
        :p (<p>->nav account-id note-id state children)
        :blockquote (<blockquote>->nav account-id note-id state children)
        :ul (<ul>->navs account-id note-id state children)
        :ol (<ul>->navs account-id note-id state children)
        :table (<table>->navs account-id note-id state children)
        :pre (<pre>->nav account-id note-id state children)
        (unknown->nav account-id note-id state children)))))

(defn- html-ast-to-note! [account-id note-id root-id node]
  (let [[_html _attributes body-node] node
        [_body _attributes div-node] body-node
        [_div _attributes & children] div-node
        state {:heading-parids (vec (repeat 7 root-id))
               :parid root-id}]
    (loop [nodes children
           state state]
      (let [node (first nodes)
            new-state (html-node-to-navs! account-id note-id state node)]
        (when-not (empty? nodes)
          (recur (rest nodes) new-state))))))

(defn markdown-to-note
  "markdown节点转成笔记"
  [account-id database-id filename text]
  (let [fixed-text (strings/replace text "\t" "  ")
        html (m2h/markdown->html fixed-text)
        node (-> (str "<div>" html "</div>")
                 (html-parser/parse-string))]
    (try
      (let [[note-id root-id] (helper-util/create-note-in-database! database-id account-id filename)]
        (html-ast-to-note! account-id note-id root-id node)
        {:success true
         :note-id note-id})
      (catch Exception e
        (u/log-error e)
        (if (strings/includes? (.getMessage e)
                               ":db.error/unique-conflict Unique conflict: :note/title+database")
          {:error (str "标题:" filename "，已经存在同名笔记，不导入")}
          {:error "系统繁忙，请稍后再试"})))))

(defn print-html-node [node depth]
  (let [prefix (strings/join (repeat depth "\t"))]
    (if (= (type node) java.lang.String)
      (println prefix node)
      (let [[tag attributes & children] node]
        (println prefix tag attributes)
        (doseq [child children]
          (print-html-node child (inc depth)))))))
