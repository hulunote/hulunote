(ns hulunote.parser
  "前后端共享编译的parser"
  (:require
   #?(:cljs
      [instaparse.core :as insta :refer-macros [defparser]]
      :clj
      [instaparse.core :as insta :refer [defparser]])))

(defparser block-parser
  "block = ( syntax-in-block / word / any-char )*
   <syntax-in-block> = (page-link | block-ref | hashtag | url-image | url-link | bold | enter | line | http-url | https-url |  italics | strikethrough | highlight | code | katex)

   page-link = <'[['> any-page-link-content <']]'>

   <page-link-chars> = #'[^\\[\\]]*'
   <any-page-link-content> = (page-link-chars | page-link)*

   block-ref = <'(('> #'[a-zA-Z0-9_\\-]+' <'))'>

   hashtag = hashtag-bare | hashtag-delimited
   <hashtag-bare> = <'#'> #'[^\\ \\+\\!\\@\\#\\$\\%\\^\\&\\*\\(\\)\\?\\\"\\;\\:\\]\\[]+'
   <hashtag-delimited> = <'#'> <'[['> any-page-link-content <']]'>

   url-image = <'!'> url-link-text url-link-url

   url-link = url-link-text url-link-url
   <url-link-text> = <'['> url-link-text-contents <']'>
   url-link-text-contents = ( (bold | backslash-escaped-right-bracket) / any-char )*
   <backslash-escaped-right-bracket> = <'\\\\'> ']'
   <url-link-url> = <'('> url-link-url-parts <')'>
   url-link-url-parts = url-link-url-part+
   <url-link-url-part> = (backslash-escaped-paren | '(' url-link-url-part* ')') / any-char
   <backslash-escaped-paren> = <'\\\\'> ('(' | ')')

   bold = <'**'> any-chars <'**'>
   italics = <'__'> any-chars <'__'>
   strikethrough = <'~~'> any-chars <'~~'>
   highlight = <'^^'> any-chars <'^^'>
   code = <'`'> any-chars <'`'>

   enter = #'\n'
   line = #'---'

   http = #'http://'

   http-url = <http> url-chars

   https = #'https://'
   https-url = <https> url-chars

   link-any-chars = link-any-char+
   <link-any-char> = #'\\w|[^A-Za-z0-9_\\[\\]]'

   url-chars = #'[^\\s\\[\\]\\(\\)\\*\\^\\{\\}]+'

   any-chars = any-char+

   <any-char> = #'\\w|\\W'
   word = #'((?!http)[ a-z0-9A-Z\u00ff-\uffff\\.\\-,.\\'\\\"])+'
   katex-any-chars = katex-any-char+
   <katex-any-char> =  #'\\w|[^A-Za-z0-9_\\$]'

   katex = <'$$'> katex-any-chars <'$$'>

   bold = <'**'> bold-any-chars <'**'>
   bold-any-chars = bold-any-char+
   <bold-any-char> =  #'\\w|[^A-Za-z0-9_\\*]'

   (* TODO: 多个italics不生效 *)
   italics = <'__'> any-chars <'__'>
   italics-any-chars = italics-any-char+
   <italics-any-char> =  #'\\w|[^A-Za-z0-9_]'

   (* TODO: 多个strikethrough不生效 *)
   strikethrough = <'~~'> any-chars <'~~'>
   strikethrough-any-chars = strikethrough-any-char+
   <strikethrough-any-char> =  #'\\w|[^A-Za-z0-9_\\~]'

   highlight = <'^^'> highlight-any-chars <'^^'>
   highlight-any-chars = highlight-any-char+
   <highlight-any-char> =  #'\\w|[^A-Za-z0-9_\\^]'

   code = <'`'> code-any-chars <'`'>
   code-any-chars = code-any-char+
   <code-any-char> =  #'\\w|[^A-Za-z0-9_\\`]'
   ")

(def regex-special  #"[~`@#$%^&*\(\)\_\-\=\+\{\}\[\]\:\"\'\<\>\\\/\|\n:]{1,2}" )

(defn parse-to-ast-
  [string]
  (if (re-find regex-special string)
    (block-parser string)
    [:block string]))

(comment
  (prn (parse-to-ast "a sdsadsa 12312~"))
  ;; [:block [:word "a sdsadsa 12312"] "~"]

  (prn (parse-to-ast "a sdsadsa 12312"))
  ;;=> [:block "a sdsadsa 12312"]

  (parse-to-ast "[[hulunote]] is best")
  ;; => [:block [:page-link "hulunote"] [:word " is best"]]

  (prn (parse-to-ast "yy ![](aaaa.png) xxx"))
  ;; [:block [:word "yy "] [:url-image [:url-link-text-contents] [:url-link-url-parts "a" "a" "a" "a" "." "p" "n" "g"]] [:word " xxx"]]

  (prn (parse-to-ast "yy __hulu__ **xxx**"))
  ;; [:block [:word "yy "] [:italics [:any-chars "h" "u" "l" "u"]] [:word " "] [:bold [:bold-any-chars "x" "x" "x"]]]

  )
(def parse-to-ast
  (memoize parse-to-ast-))

(defn combine-adjacent-strings
  [coll]
  (reduce
    (fn [elements-so-far elmt]
      (if (and (string? elmt) (string? (peek elements-so-far)))
        (let [previous-elements (pop elements-so-far)
              combined-last-string (str (peek elements-so-far) elmt)]
          (conj previous-elements combined-last-string))
        (conj elements-so-far elmt)))
    []
    coll))
