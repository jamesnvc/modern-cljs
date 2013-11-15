# Tutorial XX - sync or swim

One of the defining characteristics of the Lisp-family of languages is the
ability to take concepts from other languages and implement them at a library
level.  One of the recent examples of this is the [core.async][1] library, which
adds asynchronous communication ala-[golang][2].

## Introduction

In this tutorial, we're going to integrate `core.async` and use it to add
client-side validations to our shopping form.  In the process, we will get to
experience a new way of thinking about and reacting to events.

> NOTE 1: I suggest you keep track of your work by issuing the
> following commands at the terminal:
>
> ```bash
> git clone https://github.com/magomimmo/modern-cljs.git
> cd modern-cljs
> git checkout tutorial-22
> git checkout -b tutorial-XX-step-1
> ```

## Dependencies

Since `core.async` is a fairly bleeding-edging library, we need to bump up the
versions of clojurescript and cljsbuild to make everything work.

```clj
(defproject modern-cljs "0.1.0-SNAPSHOT"
  ...
  ...
  :dependencies [,,,
    [org.clojure/clojurescript "0.0-2014"]
    ,,,
    [org.clojure/core.async "0.1.256.0-1bf8cf-alpha"]
    ; you may need this as well to avoid dependency problems
    [org.clojure/tools.reader "0.7.10"]]
  ,,,
  :plugins [[lein-cljsbuild "1.0.0-alpha2"]
            ,,,]
```

## Adding Validation

The first thing we'll do with `core.async` is perform client-side validation of
the shopping cart form whenever a field changes.  Since we'll want to be able to
validate a single form field (as opposed to the entire form), we first add a
simple method to the end of `shopping/validatiors.clj`:

```clojure
(defn validate-field [field val]
  (field (validate-shopping-form val val val val)))
```

A slight hack, this function validates a single field using the
`validate-shopping-form` function we wrote previously by passing in the same
value for each of the fields, then just getting the errors for the field we care
about.

Next, we will add the new requirements to `shopping.cljs`:

```clojure
(ns modern-cljs.shopping
  (:require-macros [hiccups.core :refer [html]]
                   [cljs.core.async.macros :refer [go-loop]])
  (:require [domina :refer [by-id value by-class set-value! append! destroy!
                            add-class! remove-class! text set-text!]]
            [domina.events :refer [listen! prevent-default]]
            [domina.xpath :refer [xpath]]
            [hiccups.runtime :as hiccupsrt]
            [shoreleave.remotes.http-rpc :refer [remote-callback]]
            [modern-cljs.shopping.validators :refer [validate-field]]
            [cljs.core.async :refer [put! chan >! <! map<]]))
```

We will be using the `go-loop` macro from `core.async`, as well as the various
channel manipulation functions.  We also require the `validate-field`
function, some more domina dom-manipulation functions, and the domina `xpath`
function.

Next, we define a helper function to create a channel that will transmit events
from an element.

```clojure
(defn listen [el type]
  (let [out (chan)]
    (listen! el type (partial put! out))
    out))
```

The function simply creates a channel called out, uses the domina `listen!`
function to register a callback which will put the recieved event into the
channel, then returns the new channel.

With this function in place, we can define our function to asynchronously check
a given field.

```clojure
(defn check-field [field]
  (let [errs-chan (map< (fn [evt]
                          (validate-field (keyword field) (.-value (:target evt))))
                        (listen (by-id field) :change))
        label (xpath (str "//label[@for='" field "']"))
        title (text label)]
    (go-loop
      []
      (let [errs (<! errs-chan)]
        (if errs
          (-> label (add-class! "error") (set-text! (first errs)))
          (-> label (remove-class! "error") (set-text! title)))
        (recur)))))
```

This function is doing quite a few things, so let's break it down.

First, it creates a channel which will recieve errors for the field:

```clojure
  (let [errs-chan (map< (fn [evt]
                          (validate-field (keyword field) (.-value (:target evt))))
                        (listen (by-id field) :change))
```

The `map&lt;` function takes events recieved on the channel created by `listen`
and uses the `validate-field` function to create a channel of `nil`s and error
vectors.

```clojure
        label (xpath (str "//label[@for='" field "']"))
        title (text label)]
```

We get the `&lt;label&gt;` element associated with the given field using an
xpath selector and keep track of what the "default" value of the label should
be.

```clojure
(go-loop
      []
      (let [errs (<! errs-chan)]
        (if errs
          (-> label (add-class! "error") (set-text! (first errs)))
          (-> label (remove-class! "error") (set-text! title)))
        (recur)))
```

Now, the meat of the function is the "go-routine" we create.  Like in the
Go language, this creates an asynchronous process which can wait for inputs on a
channel without blocking anything else.  Instead of using the basic `go` macro,
we use `go-loop`, equivalent to `(go (loop ...``, since we want to keep
processing events forever.  The routine will wait for an error to come in on the
previously-created channel `errs-chan`, then either display an error message in
the label or restore the original state of the label.

Now, we can create one of these goroutines for each field in the form in `init`
with a simple loop over the ids of the fields.

```clojure
(defn ^:export init []
  (when (and js/document
             (aget js/document "getElementById"))
    (loop [fields '("quantity" "price" "tax" "discount")]
      (when (not (empty? fields))
        (check-field (first fields))
        (recur (rest fields))))
    (listen! (by-id "calc") :click (fn [evt] (calculate evt)))
    (listen! (by-id "calc") :mouseover add-help!)
    (listen! (by-id "calc") :mouseout remove-help!)))
```

Now, we can do our usual incantation after making changes:

```bash
lein clean-start!
```

After rebuild, go to the [shopping url][3] (making sure Javascript is enabled
now) and try putting some values in the form.  You'll see that if you put an
invalid value in one of the fields, a message will appear as soon as you leave
the input and go away once corrected.

[1]: http://clojure.github.io/core.async/
[2]: http://golang.org/
[3]: http://localhost:3000/shopping.html
